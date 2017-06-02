import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.regex.Pattern.CASE_INSENSITIVE;

public class P2 {
    private static Scanner scanner = new Scanner(System.in);
    private static Socket myClient = null;
    private static BufferedReader intBufferedReader = null;
    private static final int BIT_LEN = 16;
    private static boolean serverStopped = false;

    // r: the number of buckets is either r - 1, or r
    private static final int r = 2;
    // The current timestamp, increased by 1 when receiving each integer
    private static int current_timestamp = 0;
    private static ArrayList<LinkedList<DGIMBucket>> buckets = new ArrayList<LinkedList<DGIMBucket>>(BIT_LEN);

    public static void main(String[] args) {
        // Main thread reads in host:port pair, gets connected
        getConnect();
        // Initialize buckets
        initBuckets();
        // Main thread reads in integer stream from server, and updates DGIM data strutures;
        readIntegerStream();

        // Sub thread deals with the queries
        QueryTread queryTread = new QueryTread("Query-Tread");
        queryTread.start();

        try {
            // Main thread waits the sub-thread untile the sub-thread exits
            queryTread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    // Read in host:port pair, get connected
    private static void getConnect() {
        while (scanner.hasNextLine()) {
            // Read in host:port pair
            String tmp = scanner.nextLine();
            String[] addr = null;
            if (!tmp.contains(":")) {
                System.err.println("Invalid host:port pair, \"" + tmp + "\", discard");
                continue;
            }
            addr = tmp.split(":");
            for (int i = 0; i < addr.length; i++) {
                addr[i] = addr[i].trim();
            }

            // Check the port number, if invalid, skip and try to read next line and reconnect
            try {
                if (Integer.parseInt(addr[1]) < 1024 || Integer.parseInt(addr[1]) > 65535) {
                    System.err.println("Invalid port number: " + addr[1]);
                    continue;
                }
            } catch (NumberFormatException e) {
                System.err.println("Port format error: " + addr[1] + ". ---- Details: " +  e);
                continue;
            }

            // Get connected
            try {
                myClient = new Socket(addr[0],Integer.parseInt(addr[1]));
            } catch (UnknownHostException e) {
                System.err.println("Don't know about host: " + addr[0]);
            } catch (IOException e) {
                System.err.println("Couldn't get I/O for the connection to " + tmp);
            }

            // If connected, break; else read next line and try to re-connect
            if (myClient != null) {
                break;
            }
        }
    }

    // Initialize buckets
    private static void initBuckets() {
        for (int i = 0; i < BIT_LEN; i++) {
            buckets.add(new LinkedList<DGIMBucket>());
        }
    }

    // Reads in integer stream from server, and update DGIM data strutures;
    private static void readIntegerStream() {
        // Wait until getting connected
        while(myClient == null) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }         

        // Read in integer stream
        try {
            intBufferedReader = new BufferedReader(new InputStreamReader(myClient.getInputStream()));
        } catch (IOException e) {
            System.out.println(e);
        }
        String tmpStr;

        while(true) {
            try {
                tmpStr = intBufferedReader.readLine();

                // Reach the end, break the loop
                if (tmpStr == null || tmpStr.equals("")) {
                    System.err.println("Server stops sending integers." );
                    break;
                }

                int newInt = Integer.parseInt(tmpStr);
                System.out.println(newInt);
                updateDGIM(newInt);
            } catch (IOException e) {
                // If error occures, break the loop
                System.err.println("Error occurs, stops receiving from the server: " + e);
                break;
            }
        }

        // Close the socket
        try {
            serverStopped = true;
            intBufferedReader.close();
            myClient.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Use buckets to keep 0-1 bits
    private static void updateDGIM(int newInt) {
        for (int i = 0; i < BIT_LEN; i++) {
            if ((newInt & (1 << i)) != 0) {
                DGIMBucket newBucket = new DGIMBucket(1, current_timestamp);
                buckets.get(i).addFirst(newBucket);

                if (buckets.get(i).size() > 2) {
                    if (buckets.get(i).get(0).bucketSize == buckets.get(i).get(2).bucketSize) {
                        mergeDGIM(buckets.get(i));
                    }
                }
            }
        }
        current_timestamp++;
    }

    // Merge DGIM buckets
    private static void mergeDGIM(LinkedList<DGIMBucket> bucket) {
        Iterator<DGIMBucket> it = bucket.iterator();
        int prePreSize = 0;

        // Get the bucket size of first element, the newest element in the linkedlist
        if (it.hasNext()) {
            prePreSize = it.next().bucketSize;
        }

        DGIMBucket preBucket = it.hasNext() ? it.next(): null;
        DGIMBucket curBucket = it.hasNext() ? it.next(): null;

        while(curBucket != null && curBucket.bucketSize == prePreSize) {
            preBucket.bucketSize *= 2;
            preBucket.beginTimestamp = curBucket.beginTimestamp;
            it.remove();

            prePreSize = preBucket.bucketSize;
            preBucket = it.hasNext() ? it.next(): null;
            curBucket = it.hasNext() ? it.next(): null;
        }

    }

    // Tread class to handle queries
    static class QueryTread extends Thread {
        private Thread t;
        private String threadName;

        QueryTread(String name) {
            threadName = name;
            //System.out.println("Creating " +  threadName );
        }

        public void run() {
            String tmpStr;
            // Pattern to check the query format
            Pattern p = Pattern.compile("What is the sum for last (\\d+) integers(.?)", CASE_INSENSITIVE);

            // System.out.println("Running " +  threadName );
            // Read queries
            while(scanner.hasNextLine()) {
                tmpStr = scanner.nextLine();

                // When the query is "end" exit the P2 program
                if (tmpStr.equals("end")) {
                    System.exit(0);
                }

                Matcher m = p.matcher(tmpStr);
                if (m.matches()) {
                    // Get the number from the query
                    String intStr = tmpStr.replaceAll("[^0-9]+", "");
                    int lastK = Integer.parseInt(intStr);

                    if (lastK > 0) {
                        long lastKSum = getLastKSum(lastK);
                        System.out.println("The sum of last "+ lastK + " integers is " + lastKSum);
                    } else {
                        System.err.println("Invalid query: \"" + tmpStr + "\", discard");
                    }
                } else {
                    if (tmpStr.length() > 0) {
                        System.err.println("Invalid query: \"" + tmpStr + "\", discard");
                    }
               }
            }
        }

        public void start () {
            //System.out.println("Starting " +  threadName );
            if (t == null) {
                t = new Thread (this, threadName);
                t.start ();
            }
        }

        private long getLastKSum(int lastK) {
            double result = 0;

            // When k is greater than N, wait
            while (lastK > current_timestamp && !serverStopped) {
                try {
                    System.out.println("k is greater than N, sleep for 10ms...");
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    System.err.println(e);
                }
            }

            if (lastK > current_timestamp && serverStopped) {
                System.out.print("Warning: k is greater than N, and the server has stopped sending integers, ");
                System.out.println("k = " + lastK + ", N = " + current_timestamp + ", the result might be incorrect...");
            } 
            
            for (int i = 0; i < BIT_LEN; i++) {
                // How many bits of 1s in the buckets.get(i)
                int bits = 0;
                Iterator<DGIMBucket> it = buckets.get(i).iterator();
                DGIMBucket tmp = it.hasNext() ? it.next() : null;

                while (tmp != null && tmp.beginTimestamp > current_timestamp - lastK + 1) {
                    bits += tmp.bucketSize;
                    tmp = it.hasNext() ? it.next() : null;
                }
                if(it.hasNext()) {
                    bits += ((it.next().bucketSize) / 2);
                }
                result += bits * Math.pow(2, i);
            }
        

            return Math.round(result);
        }
    }

    static class DGIMBucket {
        public int bucketSize = 0;
        public int beginTimestamp = 0;

        public DGIMBucket(int bucketSize, int beginTimestamp) {
            this.bucketSize = bucketSize;
            this.beginTimestamp = beginTimestamp;
        }
    }
}


