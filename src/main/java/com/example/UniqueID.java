package com.example;

import java.math.BigInteger;
import java.util.Random;
import java.security.SecureRandom;
import java.io.IOException;
import org.springframework.scheduling.SchedulingTaskExecutor;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

@Component
public class UniqueID {
    private final Logger logger = LogManager.getLogger(getClass());

    final static long UINT_MAX = 4294967296L;
    private Random random = new Random();
    private SecureRandom secureRandom = new SecureRandom();

    private long id;
    BigInteger bigInteger;

    public UniqueID(
        long id,
        BigInteger bigInteger
    ) {
        this.id = id;
        this.bigInteger = bigInteger;
    }

    public UniqueID(long id) {
        this(id, null);
    }

    public UniqueID() {
        reset();
    }

    public void reset() {
        id = (long)(random.nextDouble() * UINT_MAX);
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        bigInteger = new BigInteger(randomBytes);
    }

    public long getId() {
        return id;
    }
    public void setId(long id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof UniqueID) {
            UniqueID other = (UniqueID)obj;
            return (
                id == other.id &&
                (bigInteger == other.bigInteger || (bigInteger != null && bigInteger.equals(other.bigInteger)))
            );
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Long.valueOf(id).hashCode() ^ (bigInteger != null ? bigInteger.hashCode() : 0);
    }

    @Override
    public String toString() {
        return String.format("UID: %d, BIGINT: %s", id, bigInteger.toString());
    }

    static class JoinCommandHandler implements RequestHandler {
        private final Logger logger = LogManager.getLogger(getClass());
        static final String command = "join";

        private GameGroup gameGroup;
        private MulticastPublisher publisher;

        JoinCommandHandler(GameGroup gameGroup, MulticastPublisher publisher) {
            this.gameGroup = gameGroup;
            this.publisher = publisher;
        }

        static JSONObject createRequest(UniqueID uid) {
            JSONObject retval = new JSONObject();
            retval.put(Constant.command, command);
            retval.put(
                Constant.arguments,
                new String[] {
                    Long.valueOf(uid.id).toString(),
                    uid.bigInteger.toString(10)
                }
            );
            return retval;
        }
    
        @Override
        public Boolean apply(JSONObject t) {
            try {
                logger.debug("Begin to handle the request: " + t.toString());
                JSONArray array = t.getJSONArray(Constant.arguments);
                UniqueID uid = new UniqueID(
                    array.getLong(0),
                    array.getBigInteger(1)
                );
                gameGroup.join(uid, publisher);
                return true;
            } catch (Exception e) {
                logger.error(e);
                return false;
            }
        }
    }

    public void join(MulticastPublisher publisher) throws IOException {
        var request = JoinCommandHandler.createRequest(this);
        logger.debug("Created a request: " + request.toString());
        publisher.multicast(request);
    }

    // quit from the group
    static class QuitCommandHandler implements RequestHandler {
        protected final Logger logger = LogManager.getLogger(getClass());
        static final String command = "quit";

        protected GameGroup gameGroup;

        QuitCommandHandler(GameGroup gameGroup) {
            this.gameGroup = gameGroup;
        }

        protected static JSONObject createRequest(UniqueID uid, String command) {
            JSONObject retval = new JSONObject();
            retval.put(Constant.command, command);
            retval.put(
                Constant.arguments,
                new String[] {
                    Long.valueOf(uid.id).toString(),
                    uid.bigInteger.toString(10)
                }
            );
            return retval;
        }

        static JSONObject createRequest(UniqueID uid) {
            return createRequest(uid, command);
        }
    
        @Override
        public Boolean apply(JSONObject t) {
            logger.debug("Begin to handle the request: " + t.toString());
            JSONArray array = t.getJSONArray(Constant.arguments);
            UniqueID uid = new UniqueID(
                array.getLong(0),
                array.getBigInteger(1)
            );
            gameGroup.quit(uid);
            return true;
        }
    }

    public void quit(MulticastPublisher publisher) throws IOException {
        var request = QuitCommandHandler.createRequest(this);
        logger.debug("Create a request: " + request.toString());
        publisher.multicast(request);
    }

    static class StopCommandHandler extends QuitCommandHandler {
        static final String command = "stop";
        private StopSource stopSource;
        private UniqueID self;

        StopCommandHandler(
            GameGroup gameGroup,
            StopSource stopSource,
            UniqueID self
        ) {
            super(gameGroup);
            this.stopSource = stopSource;
            this.self = self;
        }

        static JSONObject createRequest(UniqueID uid) {
            return createRequest(uid, command);
        }
    
        @Override
        public Boolean apply(JSONObject t) {
            logger.debug("Begin to handle the request: " + t.toString());
            JSONArray array = t.getJSONArray(Constant.arguments);
            UniqueID uid = new UniqueID(
                array.getLong(0),
                array.getBigInteger(1)
            );
            if (uid.equals(this.self)) {
                stopSource.requestStop();
            }
            return true;
        }
    }

    public void stop(MulticastPublisher publisher) throws IOException {
        var request = StopCommandHandler.createRequest(this);
        logger.debug("Create a request: " + request.toString());
        publisher.multicast(request);
    }

    static class HeartbeatCommandHandler implements RequestHandler {
        protected final Logger logger = LogManager.getLogger(getClass());
        static final String command = "heartbeat";

        protected GameGroup gameGroup;

        HeartbeatCommandHandler(GameGroup gameGroup) {
            this.gameGroup = gameGroup;
        }

        static JSONObject createRequest(UniqueID uid) {
            JSONObject retval = new JSONObject();
            retval.put(Constant.command, command);
            retval.put(Constant.arguments, uid.getId());
            return retval;
        }
    
        @Override
        public Boolean apply(JSONObject t) {
            //logger.debug("Begin to handle the request: " + t.toString());
            UniqueID uid = new UniqueID(t.getLong(Constant.arguments));
            gameGroup.refresh(uid);
            return true;
        }
    }

    public void startHeartbeat(SchedulingTaskExecutor executor, StopSource stopSource, MulticastPublisher publisher) throws IOException {
        Runnable heartbeat = () -> {
            while (!stopSource.stopRequested()) {
                try {
                    Thread.sleep(1000);
                } catch (Exception e) {
                }
                try {
                    var request = HeartbeatCommandHandler.createRequest(this);
                    //logger.debug("Create a request: " + request.toString());
                    publisher.multicast(request);
                } catch (Exception e) {
                    logger.debug(e);
                }
            }
        };
        executor.submit(heartbeat);
    }
}
