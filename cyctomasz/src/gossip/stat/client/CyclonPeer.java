package gossip.stat.client;

import gossip.stat.client.olsrd.IRoutingTable;
import gossip.stat.client.olsrd.OLSRDRoutingTable;
import gossip.stat.client.soap.StatServer;
import gossip.stat.client.soap.StatServerService;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.xml.namespace.QName;

import com.sun.j3d.internal.FastVector;

public class CyclonPeer implements Runnable {

	private NeighborCache neighbors;
	private List<Neighbor> favoured_neighbours;
	final private int X_OUT_FAV, RESTART_PERIOD, RESTART_PROB;
	private boolean[] prob_array;
	private int cycle_count;
	private Random rand;
	private DatagramSocket sock;
	private int pendingShuffleId;
	private StatServer statServer;
	private BlockingQueue<Boolean> responseReceived = new SynchronousQueue<Boolean>();
	public static final int MTU = 1500; // Maximum Transmission Unit: maximum
										// size of datagram package
	public static int c;
	public static int l;
//	public final static int c = 10; // cache size
//	public final static int l = 2; // message size
	public final static int socketTimeout = 3000; // sleep before shuffling
													// again and receiving
													// socket timeout
	public final static int shufflePayloadSize = l * Neighbor.recordBytes + 4;
	public final static int idLength = 4;
	final Lock neighbor_lock = new ReentrantLock();

	public CyclonPeer(InetAddress ip, int port, InetAddress statServerAddress, int statServerPort,
			int cache_size, int message_size, int cycle_count) {
		// CyclonPeer initialization phase
		this.cycle_count = cycle_count;
		c = cache_size;
		l = message_size;
		this.X_OUT_FAV = 0;
		this.RESTART_PERIOD = 0;
		this.RESTART_PROB = 0;
		try {
			this.favoured_neighbours = new ArrayList<Neighbor>();
			neighbors = new NeighborCache();
			rand = new Random();
			sock = new DatagramSocket(port, ip);
			neighbors.self = new Neighbor(ip, port);
			StatServerService _s = null;
			if (statServerAddress != null) {
				try {
					_s = new StatServerService(new URL("http://" + statServerAddress.getHostName() + ":"
							+ statServerPort + "/gossipStatServer?wsdl"), new QName("http://server.stat.gossip/",
							"StatServerService"));
				} catch (MalformedURLException e) {
					e.printStackTrace();
				}
			} else {
				_s = new StatServerService();
			}
			statServer = _s.getStatServerPort();
		} catch (SocketException e) {
			e.printStackTrace();
		}
	}

	public CyclonPeer(InetAddress ip, int port, InetAddress statServerAddress, int statServerPort,
			List<Neighbor> favoured_neighbours, int x_out_fav, int restart_period, float restart_prob,
			int cache_size,int message_size, int cycle_count) {
		// CyclonPeer initialization phase
		this.cycle_count = cycle_count;
		c = cache_size;
		l = message_size;
		if (restart_period == -1) {
			restart_period = socketTimeout;
		}
		if (restart_prob == -1) {
			restart_prob = 1;
		}
		if (x_out_fav == -1) {
			x_out_fav = 1;
		}
		this.X_OUT_FAV = x_out_fav;
		this.RESTART_PERIOD = restart_period;
		this.RESTART_PROB = Math.round(restart_prob*100);

		try {
			this.favoured_neighbours = favoured_neighbours;
			
			neighbors = new NeighborCache();
			rand = new Random();
			sock = new DatagramSocket(port, ip);
			neighbors.self = new Neighbor(ip, port);
			StatServerService _s = null;
			if (statServerAddress != null) {
				try {
					_s = new StatServerService(new URL("http://" + statServerAddress.getHostName() + ":"
							+ statServerPort + "/gossipStatServer?wsdl"), new QName("http://server.stat.gossip/",
							"StatServerService"));
				} catch (MalformedURLException e) {
					e.printStackTrace();
				}
			} else {
				_s = new StatServerService();
			}
			statServer = _s.getStatServerPort();
		} catch (SocketException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void run() {

		// shuffle thread
		Runnable r_shuffle = new Runnable() {

			@Override
			public void run() {
				try {
					// sleep for random time for asynchronous start
					Thread.sleep(rand.nextInt(socketTimeout));
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				while (!Thread.currentThread().isInterrupted()) {
					try {
						if(cycle_count <= 0){
							System.err.println("Reached maximum cycles. Exiting.");
							System.exit(0);
						}
						cycle_count--;
						shuffleInit();
						Boolean response = responseReceived.poll(socketTimeout, TimeUnit.MILLISECONDS);
						if (response == null) {
							printDebug("Deleting target neighbor from cache.");
							neighbors.removeNeighbor(neighbors.currentTarget);
							// unreachable neighbor is removed from
							// neighborCache
							// tagged Neighbors are untagged
							neighbors.untagAll();
						} else
							Thread.sleep(socketTimeout);
					} catch (IOException e) {
						e.printStackTrace();
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						System.out.println(Thread.currentThread().getName() + " : got interrupted!");
					}
				}
			}
		};

		// restart thread
		Runnable r_restart = new Runnable() {

			@Override
			public void run() {
				try {
					// sleep for random time for asynchronous start
					Thread.sleep(rand.nextInt(socketTimeout));
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				boolean[] prob_array = new boolean[100];
				for (int j = 0; j < 100; j++) {
					if (j < RESTART_PROB * 100) {
						prob_array[j] = true;
					} else {
						prob_array[j] = false;
					}
				}
				while (!Thread.currentThread().isInterrupted()) {
					neighbor_lock.lock();
					try {
						printDebug("Restart.");
						Collections.shuffle(favoured_neighbours);
						for (int i = 0; i < X_OUT_FAV && i < neighbors.neighbors.size(); i++) {
							// add favoured
							if (prob_array[rand.nextInt(100)]) {
								// check if neighbors contains favoured_neighbours.get(i)
								neighbors.removeNeighbor(neighbors.findNeighbor(favoured_neighbours.get(i)));
									if (neighbors.isFull()) {
										neighbors.neighbors.add(rand.nextInt(neighbors.neighbors.size()),
												favoured_neighbours.get(i));
									} else {
										neighbors.neighbors.add(favoured_neighbours.get(i));
									}
							}
						}
						neighbor_lock.unlock();
						Thread.sleep(RESTART_PERIOD);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} finally {
						neighbor_lock.unlock();
					}
				}

			}

		};

		if (X_OUT_FAV > 0 && RESTART_PERIOD > 0) {
			Thread restartThread = new Thread(r_restart);
			restartThread.start();
		}
		Thread shuffleThread = new Thread(r_shuffle);
		shuffleThread.start();
		int last_mes_id = 0;
		while (!Thread.currentThread().isInterrupted()) {
			// Statistikdaten an Statistik-Server senden
			List<String> edgeList = new ArrayList<String>();
			IRoutingTable routingTab = new OLSRDRoutingTable();
			InetAddress[] links = routingTab.getAllBootstrapNodes();
			for (int i = 0; i < links.length; i++) {
				edgeList.add(links[i].getHostAddress());
			}
			statServer.sendTopology(neighbors.self.getIp().getHostAddress(), edgeList);
			// Listen for Cyclon packages
			try {
				List<Neighbor> responseList;
				DatagramPacket p = new DatagramPacket(new byte[MTU], MTU);
				sock.setSoTimeout(socketTimeout); // TODO unnecessary ? if
													// changed must change
													// s.sendlist
				printDebug("Receiving");
				sock.receive(p);

				// Liste von Peers erhalten, parse sie:
				byte[] inbytes = Arrays.copyOf(p.getData(), p.getLength());
				List<Neighbor> receivedSubset = NeighborCache.neighborListFromShuffleBytes(inbytes);
				int id = NeighborCache.shuffleIdFromShuffleBytes(inbytes);
				last_mes_id = id;
				printDebug("Shuffle-Packet " + id + " von " + p.getSocketAddress() + " mit den Einträgen "
						+ receivedSubset + " erhalten.");

				// Ist das eine Antwort oder eine neue Shuffleanfrage?

				if (id != 0 && id == pendingShuffleId) {
					if (responseReceived.offer(true)) {
						printDebug("Antwort erhalten...");
						neighbor_lock.lock();
						try {
							neighbors.processResponseList(receivedSubset);
						} finally {
							neighbor_lock.unlock();
						}
						printDebug("... und eingepflegt!");
					}

				} else {
					printDebug("Anfrage erhalten...");
					neighbor_lock.lock();
					try {
						responseList = neighbors.processRequestList(receivedSubset);
					} finally {
						neighbor_lock.unlock();
					}
					printDebug("... , eingepflegt...");
					byte[] responseBytes = NeighborCache.neighborListToShuffleBytes(responseList, id);
					DatagramPacket response = new DatagramPacket(responseBytes, responseBytes.length);
					response.setSocketAddress(p.getSocketAddress());
					sock.send(response);
					printDebug("... und Antwort abgeschickt!");
				}
				statServer.sendList2(neighbors.self.getId(), neighbors.buildStatList(), id);
				printDebug("Neue Nachbarliste: " + neighbors);

			} catch (SocketTimeoutException e) {
				printDebug("Receiveing socket timed out. Restarting receiving socket.");
			} catch (IOException e) {
				e.printStackTrace();
			}/*
			 * catch (InterruptedException e){
			 * 
			 * 
			 * }
			 */
		}
		// an external interrupt occurred
		printDebug("An external interrupt occured! Interrupting shuffle Thread and leaving network.");
		shuffleThread.interrupt();
		statServer.sendList2(neighbors.self.getId(), neighbors.buildStatList(), last_mes_id);
		statServer.leave(neighbors.self.getId());
	}

	public void shuffleInit() throws IOException {
		neighbor_lock.lock();
		try {
			if (neighbors.isEmpty()) {
				printDebug("Neighbor cache is empty adding new neighbor from olsrd routing table");
				Integer bootstrapPort = null;
				InetAddress bootstrapnode = null;
				while (bootstrapPort == null) {
					IRoutingTable routingTab = new OLSRDRoutingTable();
					bootstrapnode = routingTab.getBootstrapNode();
					printDebug("bootstrapnode before subnetmaskchange: " + bootstrapnode.getHostAddress());
					printDebug("self IP: " + neighbors.self.getIp().getHostAddress());
					/*
					 * replace subnetmask with own needed when Cyclon does not
					 * run on top of OLSR bootstrapping is performed by picking
					 * a random olsr neighbor and first found active Port
					 */
					bootstrapnode = InetAddress.getByName((neighbors.self.getIp().getHostAddress()
							.substring(0, neighbors.self.getIp().getHostAddress().lastIndexOf(".")) + bootstrapnode
							.getHostAddress().substring(bootstrapnode.getHostAddress().lastIndexOf("."))));
					printDebug("bootstrapnode after subnetmaskchange: " + bootstrapnode.getHostAddress());
					// get active Port
					bootstrapPort = statServer.getBootstraskypPort(bootstrapnode.getHostAddress());
				}

				addSeedNode(bootstrapnode, bootstrapPort);
			}
			neighbor_lock.unlock();
			
			pendingShuffleId = rand.nextInt();
			if (pendingShuffleId == 0) {
				pendingShuffleId++;
			}
			neighbor_lock.lock();
			List<Neighbor> requestList = neighbors.buildRequestList(pendingShuffleId);
			byte[] requestBytes = NeighborCache.neighborListToShuffleBytes(requestList, pendingShuffleId);

			DatagramPacket request = new DatagramPacket(requestBytes, requestBytes.length);
			request.setSocketAddress(neighbors.currentTarget.getInetSocketAddress());
			if (!neighbors.self.equals(neighbors.currentTarget)) {
				sock.send(request);
				printDebug("Anfrage abgeschickt an" + neighbors.currentTarget);
			} else {
				printDebug("Würde Anfrage an sich schicken, darf nicht sein!");
			}
		} finally {
			neighbor_lock.unlock();
		}

	}

	/**
	 * Must be called before the Thread is started, for bootstrapping purposes.
	 * There is no check for duplicates.
	 * 
	 * The first neighbor is added, by adding the previous generated client.
	 * 
	 * TODO rewrite to random or find better solution
	 **/
	public void addSeedNode(InetAddress ip, int port) {
		if (!neighbors.isFull()) {
			neighbors.neighbors.add(new Neighbor(ip, port));
		}
	}

	public static void printDebug(String s) {
		System.out.println(Thread.currentThread().getName() + ": " + s);
	}
}
