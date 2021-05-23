package com.example;

import java.util.*;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RequestHandlerImpl implements RequestHandler {
    private final Logger logger = LogManager.getLogger(getClass());
    private Map<String, RequestHandler> map;

    @Autowired
    public RequestHandlerImpl(
        GameGroup gameGroup,
        MulticastPublisher publisher,
        StopSource stopSource,
        Grid grid,
        UniqueID selfUid
    ) {
        map = new HashMap<>();
        map.put(UniqueID.JoinCommandHandler.command, new UniqueID.JoinCommandHandler(gameGroup, publisher));
        map.put(GameGroup.GroupCommandHandler.command, new GameGroup.GroupCommandHandler(gameGroup));
        map.put(UniqueID.HeartbeatCommandHandler.command, new UniqueID.HeartbeatCommandHandler(gameGroup));
        map.put(UniqueID.QuitCommandHandler.command, new UniqueID.QuitCommandHandler(gameGroup));
        map.put(GameGroup.AttackCommandHandler.command, new GameGroup.AttackCommandHandler(gameGroup, publisher, grid, selfUid));
        map.put(UniqueID.StopCommandHandler.command, new UniqueID.StopCommandHandler(gameGroup, stopSource, selfUid));
    }

    @Override
    public Boolean apply(JSONObject t) {
        try {
            String command = t.getString(Constant.command);
            var rh = map.get(command);
            if (rh != null) {
                return rh.apply(t);
            } else {
                logger.error("Unsupported command: " + command);
                return false;
            }
        } catch (Exception e) {
            logger.error("Bad request: " + t.toString(), e);
            return false;
        }
    }
}
