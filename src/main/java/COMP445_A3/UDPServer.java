package COMP445_A3;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.AcceptPendingException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Arrays;
import java.util.Set;

import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;

public class UDPServer {

    private static final Logger logger = LoggerFactory.getLogger(UDPServer.class);

    private void listenAndServe(int port) throws IOException {

        try (DatagramChannel channel = DatagramChannel.open()) {
            channel.bind(new InetSocketAddress(port));
            logger.info("EchoServer is listening at {}", channel.getLocalAddress());
            ByteBuffer buf = ByteBuffer
                    .allocate(Packet.MAX_LEN)
                    .order(ByteOrder.BIG_ENDIAN);

            long SYN;
            String[] payloads = new String[0];
            boolean[] ACKs = new boolean[0];
            boolean ReceivedAllData = false;

            while (true) {
                buf.clear();
                SocketAddress router = channel.receive(buf);

                // Parse a packet from the received raw data.
                buf.flip();
                Packet packet = Packet.fromBuffer(buf);
                buf.flip();

                //first handshake
                if (packet.getType() == 2) {
                    logger.info("received first handshake");
                    SYN = packet.getSequenceNumber();
                    payloads = new String[(int) SYN];
                    ACKs = new boolean[(int)SYN];
                    ReceivedAllData = false;
                    logger.info("SYN: {}", SYN);
                    String payload = "";
                    logger.info("Packet: {}", packet);
                    logger.info("Payload: {}", payload);
                    logger.info("Router: {}", router);
                    // Send the response to the router not the client.
                    // The peer address of the packet is the address of the client already.
                    // We can use toBuilder to copy properties of the current packet.
                    // This demonstrate how to create a new packet from an existing packet.
                    // send second handshake
                    Packet resp = packet.toBuilder()
                            .setType(3)
                            .setSequenceNumber(packet.getSequenceNumber())
                            .setPayload(payload.getBytes())
                            .create();
                    channel.send(resp.toBuffer(), router);
                    logger.info("sending second handshake");
                }

                // third handshake
                if (packet.getType() == 1){
                    logger.info("received third handshake");
                    long seq = packet.getSequenceNumber();
                    String payload = "";
                    logger.info("Packet: {}", packet);
                    logger.info("Payload: {}", payload);
                    logger.info("Router: {}", router);
                    logger.info("Allow to start data transmission");
                }

                // received data
                if (packet.getType() == 0){
                    logger.info("received Data packet");
                    long seq = packet.getSequenceNumber();
                    if (ACKs[(int)seq]){
                        logger.info("Server have already received this packet {}.", seq);
                        Packet ACK = packet.toBuilder()
                                .setType(1)
                                .setSequenceNumber(packet.getSequenceNumber())
                                .setPayload("".getBytes())
                                .create();
                        channel.send(ACK.toBuffer(), router);
                        logger.info("sending Data ACK");
                        continue;
                    } else {
                        String payload = new String(packet.getPayload());
                        payloads[(int)seq] = payload;
                        ACKs[(int)seq] = true;
                        logger.info("Packet: {}", packet);
                        logger.info("Payload: {}", payload);
                        logger.info("Router: {}", router);
                        // Send the response to the router not the client.
                        // The peer address of the packet is the address of the client already.
                        // We can use toBuilder to copy properties of the current packet.
                        // This demonstrate how to create a new packet from an existing packet.
                        // send second handshake
                        Packet ACK = packet.toBuilder()
                                .setType(1)
                                .setSequenceNumber(packet.getSequenceNumber())
                                .setPayload("".getBytes())
                                .create();
                        channel.send(ACK.toBuffer(), router);
                        logger.info("sending Data ACK");
                    }
                }

                boolean getAllData = true;
                for (int o = 0; o < ACKs.length; o++){
                    if (!ACKs[o]){
                        getAllData = false;
                        break;
                    }
                }

                if (getAllData){
                    StringBuilder request = new StringBuilder();
                    for (int p = 0; p < payloads.length; p++){
                        request.append(payloads[p]);
                    }
                    ReceivedAllData = true;
                    logger.info("received all data: ");
                    logger.info("{}", request.toString());
                    logger.info("request length: {}", request.length());
                }

                // ToDo: reply to client
                if (ReceivedAllData){

                }
            }
        }
    }

    public static void main(String[] args) throws IOException {
        OptionParser parser = new OptionParser();
        parser.acceptsAll(asList("port", "p"), "Listening port")
                .withOptionalArg()
                .defaultsTo("8007");

        OptionSet opts = parser.parse(args);
        int port = Integer.parseInt((String) opts.valueOf("port"));
        UDPServer server = new UDPServer();
        server.listenAndServe(port);
    }
}
