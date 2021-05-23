package com.example;

import java.io.*;
import java.net.*;

import org.json.JSONObject;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
public class MulticastPublisher {
    MulticastAddressAndPort addressAndPort;
    private DatagramSocket socket;
    private InetAddress group;

    public MulticastPublisher(MulticastAddressAndPort addressAndPort) throws IOException {
        this.addressAndPort = addressAndPort;
        socket = new DatagramSocket();
        group = InetAddress.getByName(addressAndPort.getAddress());
    }

    public void close() throws SocketException {
        socket.close();
    }

    public void multicast(JSONObject json) throws IOException {
        byte[] buf = json.toString().getBytes();
        DatagramPacket packet = new DatagramPacket(buf, buf.length, group, addressAndPort.getPort());
        socket.send(packet);
    }
}
