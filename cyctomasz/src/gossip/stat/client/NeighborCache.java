package gossip.stat.client;

import static gossip.stat.client.CyclonPeer.c;
import static gossip.stat.client.CyclonPeer.l;
import static gossip.stat.client.CyclonPeer.shufflePayloadSize;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author Maciek Lukas
 *
 */
public class NeighborCache {

	List<Neighbor> neighbors = new CopyOnWriteArrayList<Neighbor>();
	Neighbor self, currentTarget;

	List<String> buildStatList() {
		List<String> list = new LinkedList<String>();
		for (Neighbor n : neighbors) {
			list.add(n.getId());
		}
		return list;
	}

	/**
	 * @param shuffleId
	 * @return subset List of known neighbors to send as a shuffle request
	 */
	List<Neighbor> buildRequestList(int shuffleId) {
		/**
		 * Anfrageliste für eine Shuffleinitiierung erzeugen
		 */
		incrementAllAges();
		currentTarget = getOldestNeighbor();
		currentTarget.tag();

		int leff = Math.min(l, neighbors.size());
		List<Neighbor> subset = new ArrayList<Neighbor>(l);
		subset.add(self);

		// Nachbarn durchschütteln
		Collections.shuffle(neighbors);

		int i = 0;

		while (subset.size() < leff && i < neighbors.size()) { // TODO i <
																// neighbor.size
																// überflüssig
			Neighbor nb = neighbors.get(i++);
			if (nb != currentTarget) {
				nb.tag();
				subset.add(nb);
			}

		}

		return subset;
	}

	/**
	 * 
	 * @return Neighbor with highest age within NeighborCache
	 */
	private Neighbor getOldestNeighbor() {
		Neighbor oldest = neighbors.get(0);
		for (Neighbor n : neighbors) {
			if (n.getAge() > oldest.getAge()) {
				oldest = n;
			}
		}
		return oldest;
	}

	/**
	 * 
	 * @param
	 * @return Neighbor n if nSearch exists in NeighborCache else null
	 */
	Neighbor findNeighbor(Neighbor nSearch) {
		/**
		 * Knoten anhand von IP und Port finden, Alter ignorieren
		 */
		for (Neighbor n : neighbors) {
			if (n.samePeer(nSearch)) {
				return n;
			}
		}
		return null;
	}

	/**
	 * Removes the already known peers from parameter in
	 * 
	 * @param in
	 */
	private void removeKnownFrom(List<Neighbor> in) {
		/* Löscht die in diesem Cache enthaltenen Duplikate IN DER EINGABE */
		Set<Neighbor.CompareNeighbor> uniqs = new HashSet<Neighbor.CompareNeighbor>();
		Set<Neighbor.CompareNeighbor> nodups = new HashSet<Neighbor.CompareNeighbor>();
		for (Neighbor n : neighbors) {
			uniqs.add(new Neighbor.CompareNeighbor(n));
		}
		for (Neighbor n : in) {
			nodups.add(new Neighbor.CompareNeighbor(n));
		}
		nodups.removeAll(uniqs);

		in.clear();
		for (Neighbor.CompareNeighbor n : nodups) {
			in.add(n.n);
		}
	}

	private static void removeDuplicates(List<Neighbor> in) {
		Set<Neighbor.CompareNeighbor> comps = new HashSet<Neighbor.CompareNeighbor>(); // durch
																						// das
																						// Hashset
																						// werden
																						// duplikate
																						// entfernt
		for (Neighbor n : in) {
			comps.add(new Neighbor.CompareNeighbor(n));
		}

		in.clear();

		for (Neighbor.CompareNeighbor cn : comps) {
			in.add(cn.n);
		}
	}

	/**
	 * Processes the given shuffle neighbor list without sending a response.
	 * 
	 * @param request
	 */
	void processResponseList(List<Neighbor> request) {
		/**
		 * Antwort auf eine früher von uns abgeschickte Anfrage erhalten. Wir
		 * pflegen die Updates ein und schicken selbst keine Antwort.
		 */
		// Den Anfrageknoten entfernen und Alter resetten
		if (findNeighbor(request.get(0)) != null) {
			findNeighbor(request.remove(0)).resetAge();
		}

		// Bereits bekannte Knoten und Duplikate aus dem Request entfernen
		removeKnownFrom(request);
		removeDuplicates(request);

		// Freie Plätze auffüllen:
		while (!isFull() && !request.isEmpty()) {
			// Das erhaltene Alter direkt übernehmen!
			neighbors.add(request.remove(0));
		}

		// Platz für die restlichen Antworten machen, Tags entfernen:
		int rest = request.size();
		for (Neighbor n : neighbors) {
			if (n.isTagged()) {
				if (rest > 0) {
					neighbors.remove(n);
					rest--;
				} else {
					n.untag();
				}
			}
		}

		// Die restlichen Antworten hinzufügen:
		Iterator<Neighbor> iter = request.iterator();
		while (iter.hasNext()) {
			neighbors.add(iter.next());
			iter.remove();
		}
	}

	List<Neighbor> processRequestList(List<Neighbor> request) {
		// der Request sender steht an erster Stelle
		Neighbor sender = request.get(0);
		// evtl. im Request vorhandenen Verweis auf mich selbst entfernen.
		request.remove(self);
		// Bereits bekannte Knoten aus dem Request entfernen

		removeKnownFrom(request);
		removeDuplicates(request);

		// Eine Antwort erstellen
		List<Neighbor> response = new ArrayList<Neighbor>(l);
		response.add(self);

		// Nachbarn gut mischen, um zufällige Antwortknoten zu bekommen:
		Collections.shuffle(neighbors);

		for (Neighbor n : neighbors) {
			// Falls n der request-Sender selbst ist, wird er übersprungen
			if (response.size() < l) {
				if (!n.equals(sender)) {
					response.add(n);
				}
			} else {
				break;
			}
		}

		// Solange wir noch Platz in unserer Liste haben, Knoten einfach
		// einfügen
		while (!isFull() && !request.isEmpty()) {
			neighbors.add(request.remove(0));
		}

		/*
		 * Jetzt ist unsere Liste voll. Einen der versendeten Knoten löschen;
		 * den erhaltenen einfügen.
		 */
		int i = 1; // in response.get(0) steht neighbors.self drin
		while (!(request.size() <= 1)) {
			neighbors.remove(response.get(i));
			neighbors.add(request.remove(0));
			i++;
		}

		return response;
	}

	public boolean isFull() {
		return neighbors.size() >= c;
	}

	public boolean isEmpty() {
		return neighbors.size() <= 0;
	}

	/**
	 * @param in
	 *            list of neighbors
	 * @param id
	 *            the id of the shuffle list
	 * @return id, List of neighbors as array of bytes
	 */
	static byte[] neighborListToShuffleBytes(List<Neighbor> in, int id) {
		ByteBuffer buf = ByteBuffer.allocate(shufflePayloadSize).putInt(id);
		for (Neighbor n : in) {
			for (byte b : n.toByteArray()) {
				buf.put(b);
			}
		}

		return buf.array();

	}

	static List<Neighbor> neighborListFromShuffleBytes(byte[] in) {
		ByteBuffer buf = ByteBuffer.wrap(in);
		buf.getInt(); // verschieben
		List<Neighbor> subset = new ArrayList<Neighbor>();
		while (buf.hasRemaining()) {
			byte[] neighborBuf = new byte[Neighbor.recordBytes];
			for (int i = 0; i < neighborBuf.length; i++) {
				neighborBuf[i] = buf.get();
			}
			Neighbor n = Neighbor.fromByteArray(neighborBuf);
			if (n != null) {
				subset.add(n);
			}
		}

		return subset;
	}

	static int shuffleIdFromShuffleBytes(byte[] in) {
		return ByteBuffer.wrap(in).getInt();
	}

	void incrementAllAges() {
		for (Neighbor n : neighbors) {
			n.increaseAge();
		}
	}

	public String toString() {
		return neighbors.toString();
	}

	synchronized void removeNeighbor(Neighbor removethis) {
		neighbors.remove(removethis);
	}

	public void untagAll() {
		for (Neighbor n : neighbors) {
			n.untag();
		}
	}
}
