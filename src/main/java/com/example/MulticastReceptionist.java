package com.example;

import java.net.*;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/*
1. maintain the circle;
2. check the attack request;
*/

@Primary
@Component
public class MulticastReceptionist implements Runnable {
    private final Logger logger = LogManager.getLogger(getClass());

    private MulticastSocket socket = null;
    private byte[] buf = new byte[4096];
    MulticastAddressAndPort addressAndPort;
    private RequestHandler requestHandler;
    private StopSource stopSource;
    private Latch latch;

    @Autowired
    public MulticastReceptionist(
        MulticastAddressAndPort addressAndPort,
        RequestHandler requestHandler,
        StopSource stopSource,
        Latch latch
    ) {
        this.addressAndPort = addressAndPort;
        this.requestHandler = requestHandler;
        this.stopSource = stopSource;
        this.latch = latch;
    }

    public Latch getLatch() {
        return latch;
    }

    @SuppressWarnings("deprecation")
    public void run() {
        try {
            socket = new MulticastSocket(addressAndPort.getPort());
            InetAddress group = InetAddress.getByName(addressAndPort.getAddress());
            logger.debug("Begin to listen to the address " + addressAndPort.getAddress() + " and the port: " + addressAndPort.getPort());
            socket.joinGroup(group);
            socket.setSoTimeout(1000);
            latch.countDown();
            while (!stopSource.stopRequested()) {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                try {
                    socket.receive(packet);
                    String received = new String(
                        packet.getData(), 0, packet.getLength()
                    );
                    JSONObject json = new JSONObject(received);
                    //logger.debug("Received a request: " + json.toString());
                    if (!requestHandler.apply(json)) {
                        break;
                    }
                } catch (SocketTimeoutException ste) {
                }
            }
            socket.leaveGroup(group);
            socket.close();
        } catch (Exception e) {
            logger.error(e);
        }
    }
}
