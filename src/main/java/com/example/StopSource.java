package com.example;

import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Component;

@Component
public class StopSource
{
    public static class StopToken
    {
        private AtomicBoolean token;
        StopToken(AtomicBoolean token) { this.token = token; }

        public boolean stopRequested()
        {
            return token.get();
        }
    }

    private AtomicBoolean token = new AtomicBoolean(false);

    public void requestStop()
    {
        token.set(true);
    }

    public boolean stopRequested()
    {
        return token.get();
    }

    public StopToken getToken()
    {
        return new StopToken(token);
    }
}
