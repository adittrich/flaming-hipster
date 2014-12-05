package gossip.stat.client;

import java.io.IOException;
import java.net.InetAddress;
import java.util.List;
import java.util.Random;

public class CyclonStatic {

    /**
     * @param args
     * @throws IOException 
     */
    public static void runCyclon(final int basePort, final int maxClients, final boolean isSeed,  
    		final InetAddress seedIP, final InetAddress statServerAddress, final int statServerPort, 
    		final InetAddress networkInterfaceIP, final List<Neighbor> fav_list, final int period,
    		final int num, final int prob,final int cache_size,final int message_size) throws IOException {

        Runnable peerFactory = new Runnable() {
        	
        	
            private int portOffset = 0;
            
            @Override
            public void run() {
                while (true && portOffset < maxClients) {
                    try {
                        Random r = new Random();
                        CyclonPeer p;
                        if(!fav_list.isEmpty()){
                        	p = new CyclonPeer(networkInterfaceIP, basePort + (portOffset++), statServerAddress, statServerPort,
                        			fav_list, period, num, prob, cache_size, message_size);
                        }else{
                        	p = new CyclonPeer(networkInterfaceIP, basePort + (portOffset++), statServerAddress, statServerPort, cache_size, message_size);
                        }
                        if (portOffset > 1) {
                            p.addSeedNode(networkInterfaceIP, basePort + r.nextInt(portOffset - 1));
                        }
                        new Thread(p).start();
                        Thread.sleep(r.nextInt(1500));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

            }
        };
        new Thread(peerFactory).start();
    }
}
