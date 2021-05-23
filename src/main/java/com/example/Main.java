package com.example;

import java.util.stream.Collectors;
import java.util.List;
import java.io.IOException;
import java.util.Scanner;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.SchedulingTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@SpringBootApplication
public class Main {
    private static final Logger logger = LogManager.getLogger(Main.class);

    @Bean
    public SchedulingTaskExecutor executorService() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setThreadNamePrefix("TPExecutor-");
        executor.initialize();
        return executor;
    }

    static class Opponents
    {
        long attackerId;
        long targetId;
    }

    static Opponents findOpponents(List<Long> uids, long self) {
        int n = uids.size();
        int begin = 0, end = n, mid = n;
        while (begin < end) {
            mid = (begin + end) / 2;
            long v = uids.get(mid).longValue();
            if (v < self) {
                begin = mid + 1;
                mid = end;
            } else if (v > self) {
                end = mid;
                mid = begin;
            } else {
                break;
            }
        }
        Opponents retval = new Opponents();
        retval.attackerId = uids.get(mid > 0 ? mid - 1 : n - 1);
        retval.targetId = uids.get((mid + 1) < n ? mid + 1 : 0);
        return retval;
    }

    private static void close(StopSource stopSource, MulticastPublisher publisher, SchedulingTaskExecutor executor, Scanner scanner) {
        stopSource.requestStop();
        try {
            Thread.sleep(1000);
        } catch (Exception e) {
        }
        ((ThreadPoolTaskExecutor)executor).shutdown();
        try {
            publisher.close();
        } catch (Exception e) {
            logger.warn(e);
        }
        scanner.close();
    }

    private void entry(
        ConfigurableApplicationContext ctx,
        StopSource stopSource,
        MulticastPublisher publisher,
        SchedulingTaskExecutor executor,
        Scanner scanner
    ) throws IOException, InterruptedException {
        var gameGroup = ctx.getBean(GameGroup.class);

        var receptionist = ctx.getBean(MulticastReceptionist.class);
        executor.submit(receptionist);
        receptionist.getLatch().await();
        var selfUid = ctx.getBean(UniqueID.class);
        
        selfUid.join(publisher);
        gameGroup.getJoinLatch().await();
        selfUid.startHeartbeat(executor, stopSource, publisher);
        gameGroup.startTrimming(executor, stopSource);
        logger.info("UID: " + selfUid.getId());
        var grid = ctx.getBean(Grid.class);
        grid.reset();
        logger.info(grid.toString());

        Runnable shutdownHook = () -> {
            close(stopSource, publisher, executor, scanner);
        };
        Runtime.getRuntime().addShutdownHook(new Thread(shutdownHook));

        var uids = gameGroup.getGamerUids();
        if (uids.size() < 2) {
            System.out.print("Wait for other players (you may press Ctrl-C to quit) ");
            while (uids.size() < 2) {
                try {
                    gameGroup.announceGroup(publisher);
                    Thread.sleep(2000);
                } catch (Exception e) {
                }
                System.out.print('.');
                uids = gameGroup.getGamerUids();
            }
        }
        uids = uids.stream().sorted().collect(Collectors.toList());
        System.out.println("\nAll players:");
        for (Long n : uids) {
            System.out.printf("\t%d%n", n.longValue());
        }
        var opponents = findOpponents(uids, selfUid.getId());
        System.out.printf(
            "%d will attack you, you will attack %d%n(NOTE: it is just a hint, your attacker and target may be changed dynamically)%n",
            opponents.attackerId, opponents.targetId
        );

        if (uids.size() > 2) {
            try {
                System.out.printf("If you want to move to other place, you may input two unique ids and then you will be between of them.%nOr press N + Enter to continue%n");
                String v = scanner.next();
                if (v != null && !v.equals("")) {
                    long uid1 = Long.parseLong(v);
                    v = scanner.next();
                    if (v != null && !v.equals("")) {
                        long uid2 = Long.parseLong(v);
                        if (uid1 != uid2 && uid1 != selfUid.getId() && uid2 != selfUid.getId()) {
                            selfUid.quit(publisher);
                            selfUid.setId((uid1 + uid2) / 2);
                            selfUid.join(publisher);
                        }
                    }
                }
            } catch (NumberFormatException nfe) {
            } catch (Exception e) {
                logger.debug("When trying to move to other place, an exception occurred", e);
            }
        }
        System.out.printf("Input two numbers to specify the coordinate to attack your target:");
        while (gameGroup.hasMoreThanOneMember() && !stopSource.stopRequested()) {
            int row = -1, col = -1;
            try {
                if (scanner.hasNext()) {
                    row = scanner.nextInt();
                    if (scanner.hasNext()) {
                        col = scanner.nextInt();
                    }
                }
            } catch (Exception e) {
                logger.debug(e);
            }

            if (row >= 0 && row < grid.getRows() && col >= 0 && col <= grid.getCols()) {
                gameGroup.attack(selfUid, publisher, row, col);
            }
        }
        logger.debug("The last line of the function Main::entry is being executed");
    }

    public static void main(String[] args) {
        try {
            var ctx = SpringApplication.run(Main.class, args);
            var stopSource = ctx.getBean(StopSource.class);
            var executor = ctx.getBean("executorService", SchedulingTaskExecutor.class);
            var publisher = ctx.getBean(MulticastPublisher.class);
            var scanner = new Scanner(System.in);
            Runnable task = () -> {
                try {
                    new Main().entry(ctx, stopSource, publisher, executor, scanner);
                } catch (Exception e) {
                    logger.error(e);
                }
            };
            Thread worker = new Thread(task);
            worker.start();
            while (!stopSource.stopRequested()) {
                try {
                    Thread.sleep(1000);
                } catch (Exception e) {
                }
            }
            close(stopSource, publisher, executor, scanner);
            worker.interrupt();
            worker.join();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
