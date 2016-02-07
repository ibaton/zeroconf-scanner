package se.treehou.ng.zeroconfscanner.scanner;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceListener;
import javax.jmdns.ServiceTypeListener;

import se.treehou.ng.zeroconfscanner.util.ThreadPool;

public class Scanner {

    public static final String TAG = Scanner.class.getSimpleName();

    private Context context;
    private JmDNS dnsService;
    private WifiManager.MulticastLock lock;
    private WifiManager wifi;

    private DiscoveryListener discoveryListener;
    private ServiceListener serviceListener;

    private BroadcastReceiver broadcastReceiver;

    private List<String> scanTypes = new ArrayList<>();
    private Map<String, ServiceEvent> services = new HashMap<>();

    public Scanner(Context context) {
        this.context = context;

        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                updateZeroconfListener();
            }
        };

        serviceListener = new ServiceListener() {
            @Override
            public void serviceAdded(ServiceEvent event) {
                dnsService.requestServiceInfo(event.getType(), event.getName());

                Log.d(TAG, "Added event " + event.getName());
            }

            @Override
            public void serviceRemoved(ServiceEvent event) {
                services.remove(generateId(event));
                updateServices();

                Log.d(TAG, "Removed event " + event.getName());
            }

            @Override
            public void serviceResolved(ServiceEvent event) {
                services.put(generateId(event), event);
                updateServices();

                Log.d(TAG, "Resolved event " + event.getName() + " " + event.getType());
            }
        };
    }

    public void scanFor(String scanType){
        List<String> scanTypes = new ArrayList<>();
        scanFor(scanTypes);
    }

    public void scanFor(List<String> scanTypes){
        this.scanTypes = scanTypes;
    }

    private static String generateId(ServiceEvent event){
        return event.getType()+event.getName();
    }

    private void updateZeroconfListener(){
        ThreadPool.instance().submit(new Runnable() {
            @Override
            public void run() {

                if(lock == null) {
                    lock = wifi.createMulticastLock("JmdnsLock");
                    lock.setReferenceCounted(true);
                    lock.acquire();
                }

                if(dnsService != null){
                    dnsService.unregisterAllServices();
                    try {
                        dnsService.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                try {
                    WifiInfo wifiinfo = wifi.getConnectionInfo();
                    int intaddr = wifiinfo.getIpAddress();
                    byte[] byteaddr = BigInteger.valueOf(intaddr).toByteArray();
                    InetAddress addr = InetAddress.getByAddress(byteaddr);

                    dnsService = JmDNS.create(addr);

                    if(scanTypes.size() <= 0) {
                        dnsService.addServiceTypeListener(new ServiceTypeListener() {
                            @Override
                            public void serviceTypeAdded(ServiceEvent event) {
                                dnsService.addServiceListener(event.getType(), serviceListener);
                            }

                            @Override
                            public void subTypeForServiceTypeAdded(ServiceEvent event) {

                            }
                        });
                    } else {
                        for(String scanType : scanTypes) {
                            dnsService.addServiceListener(scanType, serviceListener);
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * start scanning for new services.
     */
    public void startScan(){
        context.registerReceiver(broadcastReceiver, new IntentFilter("android.net.conn.CONNECTIVITY_CHANGE"));
        ThreadPool.instance().submit(new Runnable() {
            @Override
            public void run() {
                wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
                updateZeroconfListener();
            }
        });
    }

    /**
     * Stop scanning for new services.
     */
    public void stopScan() {
        context.unregisterReceiver(broadcastReceiver);
        services.clear();
        ThreadPool.instance().submit(new Runnable() {
            @Override
            public void run() {
                try {
                    dnsService.unregisterAllServices();
                    dnsService.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if (lock != null) lock.release();
            }
        });
    }

    public void setServerDiscoveryListener(DiscoveryListener listener){
        if(listener == null){
            return;
        }

        discoveryListener = listener;
    }

    public void updateServices(){
        discoveryListener.onUpdate(new ArrayList<>(services.values()));
    }

    public interface DiscoveryListener {
        void onUpdate(List<ServiceEvent> services);
    }
}
