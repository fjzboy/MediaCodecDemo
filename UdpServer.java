package socket.demo.UDPTEST;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;


public class UdpServer {

    public static final int LOCAL_PORT = 39000;
    public static final int CLIENT_PORT = 39000;
    public static final String CLIENT_IP = "192.168.1.63";
    public static final int BUFFER_SIZE = 1024;

    public static void main(String[] args) {



        DatagramSocket serverSocket= null;
        DatagramSocket forwardSocket = null;

        try {
            serverSocket = new DatagramSocket(LOCAL_PORT);
            forwardSocket = new DatagramSocket();
            DatagramPacket forwardPacket;

            byte[] recvBuf = new byte[BUFFER_SIZE];

            DatagramPacket recvPacket
                    = new DatagramPacket(recvBuf , recvBuf.length);

            while (true) {
                serverSocket.receive(recvPacket);
                //String recvStr = new String(recvPacket.getData(), 0, recvPacket.getLength());
                int port = recvPacket.getPort();
                InetAddress addr = recvPacket.getAddress();


                forwardPacket = new DatagramPacket(recvPacket.getData(), recvPacket.getLength(),
                        InetAddress.getByName(CLIENT_IP), CLIENT_PORT);

                forwardSocket.send(forwardPacket);
                StringBuilder sb = new StringBuilder();
                sb.append("len =" + recvPacket.getLength()).append("\n");
                sb.append("from: " + addr.getHostAddress());

                System.out.println(sb.toString());
            }

        } catch (SocketException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (serverSocket != null) {
                serverSocket.close();
            }

            if (forwardSocket != null) {
                forwardSocket.close();
            }
        }


    }
}
