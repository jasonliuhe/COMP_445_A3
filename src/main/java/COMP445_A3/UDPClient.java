package COMP445_A3;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.IntStream;

import static java.nio.channels.SelectionKey.OP_READ;

public class UDPClient {

    private static final Logger logger = LoggerFactory.getLogger(UDPClient.class);

    private static void runClient(SocketAddress routerAddr, InetSocketAddress serverAddr, String request) throws IOException {
        try(DatagramChannel channel = DatagramChannel.open()){
            int dataLength = request.getBytes().length;
            System.out.println("Data length: " + dataLength);
            int NumOfPacket = dataLength/1014+1;
            System.out.println("Number of packet: " + NumOfPacket);

            // Three-way handshake

            // First handshake
            while (true){
                // create packet for handshake 1
                String SYN = "1";
                Packet HS1 = new Packet.Builder()
                        .setType(2)     // type: SYN
                        .setSequenceNumber(1L)      // SYN 0
                        .setPortNumber(serverAddr.getPort())
                        .setPeerAddress(serverAddr.getAddress())
                        .setPayload("".getBytes())
                        .create();
                // send first handshake packet to server
                channel.send(HS1.toBuffer(), routerAddr);

                logger.info("Sending first handshake to router at {}", routerAddr);

                // Try to receive second handshake within timeout.
                channel.configureBlocking(false);
                Selector selector = Selector.open();
                channel.register(selector, OP_READ);
                logger.info("Waiting for the Second handshake");
                selector.select(500);

                Set<SelectionKey> keys = selector.selectedKeys();
                // If not get response, will resent first handshake
                if(keys.isEmpty()){
                    logger.error("No response after timeout");
                }
                // if get response
                else {
                    ByteBuffer buf = ByteBuffer.allocate(Packet.MAX_LEN);
                    SocketAddress router = channel.receive(buf);
                    buf.flip();
                    Packet resp = Packet.fromBuffer(buf);
                    logger.info("received second handshake: ");
                    logger.info("Packet: {}", resp);
                    logger.info("Router: {}", router);
                    logger.info("Sequence Number: {}", resp.getSequenceNumber());
                    String payload = new String(resp.getPayload(), StandardCharsets.UTF_8);
                    logger.info("Payload: {}",  payload);

                    keys.clear();

                    // send third handshake
                    long ACK = resp.getSequenceNumber();
                    Packet HS3 = new Packet.Builder()
                            .setType(1)
                            .setSequenceNumber(ACK)
                            .setPortNumber(serverAddr.getPort())
                            .setPeerAddress(serverAddr.getAddress())
                            .setPayload("".getBytes())
                            .create();
                    channel.send(HS3.toBuffer(), routerAddr);
                    logger.info("Sending third handshake.");
                    logger.info("Allow to start data transmission");

                    // Starting transfer data
                    int windowSize = 5;
                    long[] seq = IntStream.range(0, windowSize*2).mapToLong(i -> 0).toArray();

                    Packet Data = new Packet.Builder()
                            .setType(0)
                            .setSequenceNumber(0)
                            .setPortNumber(serverAddr.getPort())
                            .setPeerAddress(serverAddr.getAddress())
                            .setPayload(request.getBytes())
                            .create();
                    channel.send(Data.toBuffer(), routerAddr);
                    logger.info("Sending Data.");

                    // Try to receive Data ACK
                    channel.configureBlocking(false);
                    selector = Selector.open();
                    channel.register(selector, OP_READ);
                    logger.info("Waiting for the Data ACK");
                    selector.select(500);

                    keys = selector.selectedKeys();
                    // If not receive Data ACK
                    if(keys.isEmpty()){
                        logger.error("No response after timeout");
                    } else {
                        // if receive Data ACK
                        buf = ByteBuffer.allocate(Packet.MAX_LEN);
                        router = channel.receive(buf);
                        buf.flip();
                        resp = Packet.fromBuffer(buf);
                        logger.info("received Data ACK: ");
                        logger.info("Packet: {}", resp);
                        logger.info("Router: {}", router);
                        logger.info("Sequence Number: {}", resp.getSequenceNumber());
                        payload = new String(resp.getPayload(), StandardCharsets.UTF_8);
                        logger.info("Payload: {}",  payload);
                        logger.info("Data transfer success");

                        keys.clear();
                    }

//                    for (int i = 0; i < seq.length; i++){
//                        System.out.println("seq[" + i +"]: " + seq[i]);
//                    }

                    break;
                }
            }


//            Packet p = new Packet.Builder()
//                    .setType(0)
//                    .setSequenceNumber(1L)
//                    .setPortNumber(serverAddr.getPort())
//                    .setPeerAddress(serverAddr.getAddress())
//                    .setPayload(request.getBytes())
//                    .create();
//            channel.send(p.toBuffer(), routerAddr);
//
//            logger.info("Sending \"{}\" to router at {}", request, routerAddr);

            // Try to receive a packet within timeout.
//            channel.configureBlocking(false);
//            Selector selector = Selector.open();
//            channel.register(selector, OP_READ);
//            logger.info("Waiting for the response");
//            selector.select(5000);
//
//            Set<SelectionKey> keys = selector.selectedKeys();
//            if(keys.isEmpty()){
//                logger.error("No response after timeout");
//                return;
//            }

            // We just want a single response.
//            ByteBuffer buf = ByteBuffer.allocate(Packet.MAX_LEN);
//            SocketAddress router = channel.receive(buf);
//            buf.flip();
//            Packet resp = Packet.fromBuffer(buf);
//            logger.info("Packet: {}", resp);
//            logger.info("Router: {}", router);
//            String payload = new String(resp.getPayload(), StandardCharsets.UTF_8);
//            logger.info("Payload: {}",  payload);
//
//            keys.clear();
        }
    }

    public static void main(String[] args) throws IOException {
        OptionParser parser = new OptionParser();
        parser.accepts("router-host", "Router hostname")
                .withOptionalArg()
                .defaultsTo("localhost");

        parser.accepts("router-port", "Router port number")
                .withOptionalArg()
                .defaultsTo("3000");

        parser.accepts("server-host", "EchoServer hostname")
                .withOptionalArg()
                .defaultsTo("localhost");

        parser.accepts("server-port", "EchoServer listening port")
                .withOptionalArg()
                .defaultsTo("8007");

        OptionSet opts = parser.parse(args);

        // Router address
        String routerHost = (String) opts.valueOf("router-host");
        int routerPort = Integer.parseInt((String) opts.valueOf("router-port"));

        // Server address
        String serverHost = (String) opts.valueOf("server-host");
        int serverPort = Integer.parseInt((String) opts.valueOf("server-port"));
        SocketAddress routerAddress = new InetSocketAddress(routerHost, routerPort);
        InetSocketAddress serverAddress = new InetSocketAddress(serverHost, serverPort);

//        StringBuilder request = new StringBuilder();
//        int numC = 1013;
//        for (int i = 0; i < numC; i++){
//            request.append("1");
//        }
        String request = "hello world";
        runClient(routerAddress, serverAddress, request);
    }
}

