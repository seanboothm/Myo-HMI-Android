package com.ucla.max.DataProducer;

import java.io.*;
import java.net.*;
import java.util.Properties;
import java.util.Arrays;
import java.util.ArrayList;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.commons.lang3.ArrayUtils;

public class DataProducer {
    
    public static String PC_IP = "2601:645:c100:b669:ad86:cf34:9b81:48e3";
    public static String ANDROID_IP = "2601:645:c100:b669:1be:8aec:d823:21e1";//"2601:645:c100:b669:7936:fb0e:af30:dfe0"; //"2601:645:c100:b669:e814:5ed6:d407:fc13";
    public static Integer PC_PORT = 9940;
    public static Integer ANDROID_PORT = 9941;
    
    private static FeatureCalculator fcalc;

    static int packetLength;

    static OutputStream myos;

    static long time1 = 0;
    static long time2 = 0;
	
	public static void startServer() {
            // initialize a Socket for TCP/IP communication with Android device.
            System.out.printf("Preparing to start server...\n");

            ServerSocket echoServer = null;

            Socket clientSocket = null;
            
            ServerSocket androidServer = null;

            Socket androidSocket = null;

            System.out.printf("Initializing PC-Server Socket...\n");
            try {
               echoServer = new ServerSocket(PC_PORT);
            } catch (IOException e) {
               System.out.println(e);
            }
            
            System.out.printf("Initializing Android Server Socket...\n");
            try {
               androidServer = new ServerSocket(ANDROID_PORT);
            } catch (IOException e) {
               System.out.println(e);
            }

	    try {
                // waiting for client connection
                clientSocket = echoServer.accept();
                
                System.out.printf("Connection1 with client established. Listening for incoming messages...\n");
                
                androidSocket = androidServer.accept();
                
                System.out.printf("Connection2 with client established. Listening for incoming messages...\n");

                InputStream myis = clientSocket.getInputStream();
                
                myos = androidSocket.getOutputStream();

                byte[] buffer = new byte[73];

                int length;

                int trainCount = 0;

                int samples = 100;
                
                packetLength = 73;

                boolean trained = false;

                while(true){

                    if ((length = myis.read(buffer)) != -1) {

                        //System.out.println("Packet Length: " + length );
			System.out.println("EMG1: " + Arrays.toString(buffer));
	                 //System.out.println(length);

			    if(length==73){ //length == 73?

                            byte[] time = Arrays.copyOfRange(buffer, 65, 73);
                            long clienttime = bytesToLong(time);
                            
                            if(!trained){//blocks broken packets
                                switch(buffer[0]){
                                    case 0:
                                        //System.out.println("EMG1: " + Arrays.toString(buffer));
                                        //route data nowhere
                                        break;
                                    case 1:
                                        //classify
                                        if((fcalc.getSize()%samples)==0){ //Baaaaaad solution just for testing, need to remove dependency on local operation
                                            fcalc.setTrain(false);
                                            fcalc.setClassify(true);
                                            trained = true;
                                        }
                                        break;
                                    case 2:
                                        //train
                                        fcalc.setClassify(false);
                                        fcalc.setTrain(true);
                                        break;
                                    case 3:
                                        System.out.println(buffer);
                                    default:
                                        //broken packet
                                        break;
                                }
                            }

                            // ArrayList<DataVector> FullWindow = new ArrayList<>();

                            for(int i = 0; i< 64/8; i++){
                                byte[] emg_data = Arrays.copyOfRange(buffer,i*8+1,i*8+1+8);
                                Number[] emg_dataObj = ArrayUtils.toObject(emg_data);
                                ArrayList<Number> emg_data_list = new ArrayList<>(Arrays.asList(emg_dataObj));
                                DataVector dvec = new DataVector(true, 1, 8, emg_data_list, System.currentTimeMillis());

                                fcalc.pushFeatureBuffer(dvec);

                                // FullWindow.add(dvec);
                            }

                            // fcalc.pushFeatureBuffer(FullWindow);

                            if(fcalc.getClassify()){
                                myos.write(fcalc.getPrediction());
                                // System.out.println(fcalc.getPrediction());
                                time2 = time1;// ?
                                time1 = System.nanoTime();
                            }

                            myos.write(0);
                            time2 = time1;// ?
                            time1 = System.nanoTime();
                            long latency = (time1 - time2) - clienttime;
                            //System.out.println("Time1 - Time2: " + (time1-time2) + " Client Time: " + clienttime + " Round Trip Latency: " + latency);
                        }
                    }
                }

	     } catch (IOException e) {
	           System.out.println(e);
	     }
	}	

    public static long bytesToLong(byte[] b) {
        long result = 0;
        for (int i = 0; i < 8; i++) {
            result <<= 8;
            result |= (b[i] & 0xFF);
        }
        return result;
    }
	
	public static void main(String args[]) {
            fcalc = new FeatureCalculator();
            startServer();
	}
}
