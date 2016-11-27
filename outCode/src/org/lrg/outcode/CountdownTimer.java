package org.lrg.outcode;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CountdownTimer {

	private static Map<String, List<Instant>> started = new HashMap<String, List<Instant>>();
	private static Map<String, List<Instant>> stopped = new HashMap<String, List<Instant>>();

	public static void start(String name) {
		if (started.get(name) == null)
			started.put(name, new ArrayList<Instant>());
		started.get(name).add(Instant.now());
	};

	public static void stop(String name) {
		if (stopped.get(name) == null)
			stopped.put(name, new ArrayList<Instant>());
		stopped.get(name).add(Instant.now());
	};

	public static void printAndReset(String name) {
		List<Instant> start = started.get(name);
		List<Instant> stop = stopped.get(name);
		Duration total = Duration.ZERO;
		if (start != null && stop != null){
			for (int i = 0; i < start.size(); i++){
				total = total.plus(Duration.between(start.get(i), stop.get(i)));
			}
			
			System.out.println(name + " " + total.toString().replaceFirst("PT", "").replaceFirst("M", " min ").replaceFirst("S", " s"));
			started.remove(name);
			stopped.remove(name);
		}
	};
}
