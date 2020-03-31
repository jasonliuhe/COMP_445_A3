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

import static java.nio.channels.SelectionKey.OP_READ;

public class UDPClient {

    private static final Logger logger = LoggerFactory.getLogger(UDPClient.class);

    private static void ClientSendTo (SocketAddress routerAddr, InetSocketAddress serverAddr, String request) throws IOException {
        try(DatagramChannel channel = DatagramChannel.open()){
            int dataLength = request.getBytes().length;
            long NumOfPacket = dataLength/1014+1;

            // create packets list
            Packet[] packets = new Packet[(int) NumOfPacket];
            for (int i = 0; i < packets.length; i++){
                if (i == packets.length-1){
                    packets[i] = new Packet.Builder()
                        .setType(0)
                        .setSequenceNumber(i)
                        .setPortNumber(serverAddr.getPort())
                        .setPeerAddress(serverAddr.getAddress())
                        .setPayload(request.substring(i*1013).getBytes())
                        .create();
//                    logger.info("{}", request.substring(i*1014-1).getBytes().length);
                } else {
                    packets[i] = new Packet.Builder()
                        .setType(0)
                        .setSequenceNumber(i)
                        .setPortNumber(serverAddr.getPort())
                        .setPeerAddress(serverAddr.getAddress())
                        .setPayload(request.substring(i*1013, 1013+i*1013).getBytes())
                        .create();
//                    logger.info("{}", request.substring(i*1014, 1013+i*1014).getBytes().length);
                }
            }

            // Three-way handshake

            // First handshake
            while (true){
                // create packet for handshake 1
                Packet HS1 = new Packet.Builder()
                        .setType(2)     // type: SYN
                        .setSequenceNumber(NumOfPacket)      // SYN
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

                    Packet HS3 = new Packet.Builder()
                            .setType(1)
                            .setSequenceNumber(0L)
                            .setPortNumber(serverAddr.getPort())
                            .setPeerAddress(serverAddr.getAddress())
                            .setPayload("".getBytes())
                            .create();
                    channel.send(HS3.toBuffer(), routerAddr);
                    logger.info("Sending third handshake to router at {}", routerAddr);

                    keys.clear();

                    break;
                }
            }

            // Starting transfer data
            logger.info("Start to sending data to router {}", routerAddr);
            int windowSize = 4;
            boolean[] ACKs = new boolean[packets.length];

            while (true) {
                // if packets number is less than window size
                // maximum of payload is 4052 bytes
                if (packets.length <= windowSize){
                    for (int i = 0; i < packets.length; i++){
                        if (!ACKs[i]){
                            channel.send(packets[i].toBuffer(), routerAddr);
                            logger.info("Sending packet {}", packets[i].getSequenceNumber());
                            logger.info("Payload: {}", new String(packets[i].getPayload()));
                            logger.info("Payload length: {}", new String(packets[i].getPayload()).length());
                        }
                    }
                    // Try to receive Data ACK
                    for (int i = 0; i < packets.length; i++){
                        channel.configureBlocking(false);
                        Selector selector = Selector.open();
                        channel.register(selector, OP_READ);
                        logger.info("Waiting for the Data ACK");
                        selector.select(500);

                        Set<SelectionKey> keys = selector.selectedKeys();
                        // If not receive Data ACK
                        if (keys.isEmpty()) {
                            logger.error("No response after timeout");
                        } else {
                            // if receive Data ACK
                            ByteBuffer buf = ByteBuffer.allocate(Packet.MAX_LEN);
                            SocketAddress router = channel.receive(buf);
                            buf.flip();
                            Packet resp = Packet.fromBuffer(buf);
                            logger.info("received Data ACK: ");
                            logger.info("Packet: {}", resp);
                            logger.info("Router: {}", router);
                            logger.info("Sequence Number: {}", resp.getSequenceNumber());
                            ACKs[(int) resp.getSequenceNumber()] = true;
                            String payload = new String(resp.getPayload(), StandardCharsets.UTF_8);
                            logger.info("Payload: {}", payload);
                            // display ACKs
//                            for (int o = 0; o < ACKs.length; o++){
//                                logger.info("ACKs[{}]: {}", o, ACKs[o]);
//                            }
                            keys.clear();
                            boolean getAllACKs = true;
                            for (int p = 0; p < ACKs.length; p++){
                                if (!ACKs[p]){
                                    getAllACKs = false;
                                }
                            }
                            if (getAllACKs){
                                break;
                            }
                        }
                    }
                    // check all the packets get ACK.
                    boolean getAllACKs = true;
                    for (int p = 0; p < ACKs.length; p++){
                        if (!ACKs[p]){
                            getAllACKs = false;
                        }
                    }
                    if (getAllACKs){
                        logger.info("Data transfer success");
                        break;
                    }
                } else {

                }
            }
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

        StringBuilder request = new StringBuilder();
        int numC = 4052;
        for (int i = 0; i < numC; i++){
            request.append("1");
        }
        logger.info("request: {}", request.toString());
//        logger.info(request.toString());
//        String request = "hello world";
        ClientSendTo(routerAddress, serverAddress, request.toString());
    }
}

