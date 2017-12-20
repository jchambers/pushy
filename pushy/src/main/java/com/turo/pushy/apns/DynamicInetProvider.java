package com.turo.pushy.apns;

import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Name;
import org.xbill.DNS.Record;
import org.xbill.DNS.SimpleResolver;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Type;

/**
 * for creating new connections dynamicly to different gateways
 * 
 * for DEMO
 * DynamicInetProvider provider = new DynamicInetProvider("api.push.apple.com", ApnsClientBuilder.DEFAULT_APNS_PORT);
 * provider.addDnsServer("114.114.114.114");
 * provider.addDnsServer("223.5.5.5");
 * provider.addDnsServer("180.76.76.76");
 * provider.start();
 * try {
 *     Thread.sleep(5000); //wait for fetching address async
 * } catch (InterruptedException e1) {
 * }
 * int count = 8;
 * while(count -- > 0) {
 *     System.out.println(provider.getInetAddress());
 * }
 * 
 * @author hblzxsj
 *
 */
public class DynamicInetProvider implements ApnsInetProvider {
    String host;
    int port;
    HostnameResolver resolver;
    boolean shouldStop = false;
    Map<String, Long> hostMap;
    List<String> hostList;
    List<SimpleResolver> dnsServers;
    int ttl = 300;
    int fetchInterval = 60;
    
    class HostnameResolver extends Thread {
        
        public HostnameResolver() {
            super();
            setDaemon(true);
        }
        
        @Override
        public void run() {
            HashSet<String> ipList = new HashSet<>();
            Name name;
            try {
                name = new Name(host);
            } catch (TextParseException e1) {
                throw new RuntimeException("hostname format illegal");
            }
            Lookup lookup = new Lookup(name, Type.A);
            lookup.setCache(null);
            Record[] records;
            while (!shouldStop) {
                ipList.clear();
                for (SimpleResolver resolver : dnsServers) {
                    lookup.setResolver(resolver);
                    records = lookup.run();
                    if (records == null || records.length == 0) {
                        continue;
                    }
                    for (Record record : records) {
                        ipList.add(record.rdataToString());
                    }
                }
                synchronized (hostList) {
                    long now = System.currentTimeMillis();
                    for (String ip : ipList) {
                        hostMap.put(ip, now);
                        if (!hostList.contains(ip)) {
                            hostList.add(ip);
                        }
                    }
                    //remove expires
                    Iterator<Entry<String, Long>> iterator = hostMap.entrySet().iterator();
                    while (iterator.hasNext()) {
                        Entry<String, Long> entry = iterator.next();
                        if (now - entry.getValue() > ttl * 1000) {
                            hostList.remove(entry.getKey());
                            iterator.remove();
                        }
                    }
                }
                try {
                    int counter = fetchInterval;
                    while (counter -- > 0) {
                        sleep(1000);
                        if (shouldStop) {
                            break;
                        }
                    }
                } catch (InterruptedException e) {
                }
            }
        }
    }
    
    public DynamicInetProvider(String host, int port) {
        this.host = host;
        this.port = port;
        this.resolver = new HostnameResolver();
        this.hostMap = new HashMap<>(100);
        this.hostList = new ArrayList<>();
        this.dnsServers = new ArrayList<>();
    }

    @Override
    public InetSocketAddress getInetAddress() {
        if (hostMap.size() < 1) {
            //fail back
            return new InetSocketAddress(host, port);
        }
        synchronized (hostList) {
            int index = (int) (Math.random() * hostList.size());
            return new InetSocketAddress(hostList.get(index), port);
        }
    }
    
    public void addDnsServer(String host) {
        try {
            dnsServers.add(new SimpleResolver(host));
        } catch (UnknownHostException e) {
        }
    }
    
    public void start() {
        this.resolver.start();
    }
    
    public String[] getHostList() {
        return hostList.toArray(new String[] {});
    }

    public void shutdown() {
        shouldStop = true;
    }
    
    /**
     * set the expire time in seconds of the ip records
     * @param ttl
     */
    public void setTTL(int ttl) {
        this.ttl = ttl;
    }

    /**
     * set the interval in seconds between trying to retrieve new ips from dns 
     * @param fetchInterval
     */
    public void setFetchInterval(int fetchInterval) {
        this.fetchInterval = fetchInterval;
    }

}
