package com.example;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.locks.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.SchedulingTaskExecutor;

import org.json.JSONObject;
import org.json.JSONArray;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.springframework.stereotype.Component;

@Component
public class GameGroup {
    private final Logger logger = LogManager.getLogger(getClass());

    public static class State implements Comparable<State> {
        public final static int DEFAULT_HEARTBEAT = 6;
        public long uid;
        public int  heartbeat = DEFAULT_HEARTBEAT;

        @Override
        public int compareTo(State o) {
            return (int)(uid - o.uid);
        }
    }
    private Lock lock;
    private TreeSet<State> set;
    private Latch joinLatch;

    @Autowired
    public GameGroup(Latch joinLatch) {
        lock = new ReentrantLock(true);
        set = new TreeSet<State>();
        this.joinLatch = joinLatch;
    }

    public boolean hasMoreThanOneMember() {
        boolean retval = true;
        lock.lock();
        try {
            retval = set.size() > 1;
        } finally {
            lock.unlock();
        }
        return retval;
    }

    public Latch getJoinLatch() {
        return joinLatch;
    }

    static class GroupCommandHandler implements RequestHandler {
        protected final Logger logger = LogManager.getLogger(getClass());
        static final String command = "group";

        protected GameGroup gameGroup;

        GroupCommandHandler(GameGroup gameGroup) {
            this.gameGroup = gameGroup;
        }

        static JSONObject createRequest(GameGroup gameGroup) {
            JSONObject retval = new JSONObject();
            retval.put(Constant.command, command);
            List<Long> uids = gameGroup.getGamerUids();
            Long[] array = new Long[uids.size()];
            uids.toArray(array);
            retval.put(Constant.arguments, array);
            return retval;
        }
    
        @Override
        public Boolean apply(JSONObject t) {
            logger.debug("Begin to handle the request: " + t.toString());
            JSONArray array = t.getJSONArray(Constant.arguments);
            gameGroup.mergeGroup(array);
            return true;
        }
    }

    void mergeGroup(JSONArray array) {
        logger.debug(array.toString() + " will be merged into the group");
        int n = array.length();
        List<State> list = new ArrayList<>(n);
        for (int i = 0; i < n; ++i) {
            State state = new State();
            state.uid = array.getLong(i);
            list.add(state);
        }
        lock.lock();
        try {
            set.addAll(list);
        } finally {
            lock.unlock();
        }
    }

    public void announceGroup(MulticastPublisher publisher) throws IOException {
        var request = GroupCommandHandler.createRequest(this);
        logger.debug("Create a request: " + request.toString());
        publisher.multicast(request);
    }

    public boolean join(UniqueID uid, MulticastPublisher publisher) throws IOException {
        logger.debug("trying to add " + uid.toString() + " into the game group");
        State state = new State();
        state.uid = uid.getId();
        int size = 1;
        boolean retval = true;
        lock.lock();
        try {
            retval = set.add(state);
            if (!retval) {
                uid.stop(publisher);
            }
            size = set.size();
        } finally {
            lock.unlock();
        }
        if (retval) {
            logger.debug("The new user " + uid + " has been added to the group successfully");
            if (size > 1) {
                announceGroup(publisher);
            } else {
                joinLatch.countDown();
            }
        } else {
            logger.debug("The new user " + uid + " could not be added to the group");
        }
        return retval;
    }

    public void refresh(UniqueID uid) {
        State state = new State();
        state.uid = uid.getId();
        lock.lock();
        try {
            var e = set.ceiling(state);
            if (e != null && e.uid == state.uid) {
                e.heartbeat = State.DEFAULT_HEARTBEAT;
            }
        } finally {
            lock.unlock();
        }
    }

    public boolean quit(UniqueID uid) {
        State state = new State();
        state.uid = uid.getId();
        boolean retval = true;
        lock.lock();
        try {
            retval = set.remove(state);
        } finally {
            lock.unlock();
        }
        return retval;
    }

    public List<Long> getGamerUids() {
        List<Long> retval = new ArrayList<>();
        lock.lock();
        try {
            var it = set.iterator();
            while (it.hasNext()) {
                retval.add(it.next().uid);
            }
        } finally {
            lock.unlock();
        }
        return retval;
    }

    public Long getTargetUid(long num) {
        Long retval = null;
        var s = new State();
        s.uid = num;
        lock.lock();
        try {
            s = set.ceiling(s);
            if (s != null) {
                retval = s.uid;
            } else {
                retval = set.first().uid;
            }
        } finally {
            lock.unlock();
        }
        return retval;
    }

    private void trim() {
        lock.lock();
        try {
            var it = set.iterator();
            while (it.hasNext()) {
                var e = it.next();
                --e.heartbeat;
                if (e.heartbeat <= 0) {
                    logger.info(String.format("The gamer %d will be removed", e.uid));
                    it.remove();
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public void startTrimming(SchedulingTaskExecutor executor, StopSource stopSource) {
        Runnable trimming = () -> {
            while (!stopSource.stopRequested()) {
                try {
                    Thread.sleep(1000);
                } catch(Exception e) {
                }
                trim();
            }
        };
        executor.submit(trimming);
    }

    static class AttackCommandHandler implements RequestHandler {
        private final Logger logger = LogManager.getLogger(getClass());
        static final String command = "attack";

        private GameGroup gameGroup;
        private MulticastPublisher publisher;
        private Grid grid;
        private UniqueID selfUid;

        AttackCommandHandler(
            GameGroup gameGroup,
            MulticastPublisher publisher,
            Grid grid,
            UniqueID selfUid
        ) {
            this.gameGroup = gameGroup;
            this.publisher = publisher;
            this.grid = grid;
            this.selfUid = selfUid;
        }

        static JSONObject createRequest(UniqueID uid, int row, int col) {
            JSONObject retval = new JSONObject();
            retval.put(Constant.command, command);
            retval.put(
                Constant.arguments,
                new Long[] {
                    Long.valueOf(uid.getId()),
                    Long.valueOf(row),
                    Long.valueOf(col)
                }
            );
            return retval;
        }
    
        @Override
        public Boolean apply(JSONObject t) {
            try {
                logger.debug("Begin to handle the request: " + t.toString());
                JSONArray array = t.getJSONArray(Constant.arguments);
                long id = array.getLong(0) + 1; // find next one in the circle
                id = gameGroup.getTargetUid(id);

                int row = array.getInt(1);
                int col = array.getInt(2);
                if (selfUid.getId() == id && grid.getRow() == row && grid.getCol() == col) {
                    logger.info(String.format("%d lost the game", id));
                    System.out.println("******************************* GAME OVER *******************************");
                    gameGroup.quit(selfUid);
                    selfUid.stop(publisher);
                }
                return true;
            } catch (Exception e) {
                logger.error(e);
                return false;
            }
        }
    }

    public void attack(UniqueID self, MulticastPublisher publisher, int targetRow, int targetCol) throws IOException {
        var request = AttackCommandHandler.createRequest(self, targetRow, targetCol);
        logger.debug("Create a request: " + request.toString());
        publisher.multicast(request);
    }
}
