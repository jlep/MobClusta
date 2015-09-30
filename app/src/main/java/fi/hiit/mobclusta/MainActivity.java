package fi.hiit.mobclusta;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.provider.ContactsContract;
import android.support.v7.app.AppCompatActivity;
import android.support.v4.app.FragmentTransaction;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import fi.hiit.mobclusta.common.view.LogInterface;

public class MainActivity extends AppCompatActivity implements NetworkProvider {

    private WifiP2pManager mManager;
    private WifiP2pManager.Channel mChannel;
    private BroadcastReceiver mReceiver;
    private IntentFilter mIntentFilter;
    private boolean mWifiP2pEnabled = false;
    private boolean mOwnerIntent = false;
    private TextView indicatorText;
    private View indicatorLight;
    private ExecutorService pool;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pool = Executors.newCachedThreadPool();
        setContentView(R.layout.activity_main);
        indicatorText = (TextView) findViewById(R.id.indicator_text);
        indicatorLight = findViewById(R.id.indicator_light);

        if (savedInstanceState == null) {
            Log.d("savedinstance IS NULL");
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            SlidingTabsBasicFragment fragment = new SlidingTabsBasicFragment();
            transaction.replace(R.id.sample_content_fragment, fragment);
            transaction.commit();
        }

        mManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        mChannel = mManager.initialize(this, getMainLooper(), new WifiP2pManager.ChannelListener() {
            @Override
            public void onChannelDisconnected() {
                Log.d("Channel disconnected");
            }
        });
        mReceiver = new WiFiDirectBroadcastReceiver(mManager, mChannel, this);
        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
    }

    @Override
    protected void onDestroy() {
        pool.shutdown();
        super.onDestroy();
    }

    /* register the broadcast receiver with the intent values to be matched */
    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mReceiver, mIntentFilter);
    }
    /* unregister the broadcast receiver */
    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mReceiver);
    }

    public void alert(String title, String msg) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(msg)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // continue with delete
                    }
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private Set<NetworkListener> networkListeners = new HashSet<>();

    public void wifiEnabled(boolean enabled) {
        if (enabled) {
            indicatorText.setText("Disconnected");
            indicatorLight.setBackground(getResources().getDrawable(R.drawable.circle));
        } else {
            indicatorText.setText("Unavailable");
            indicatorLight.setBackground(getResources().getDrawable(R.drawable.circle));
        }
        mWifiP2pEnabled = enabled;
        Iterator<NetworkListener> iterator = networkListeners.iterator();
        while (iterator.hasNext()) {
            NetworkListener listener = iterator.next();
            if (listener == null) {
                iterator.remove();
            } else {
                listener.wifiP2pEnabled(enabled);
            }
        }
    }

    @Override
    public void setOwnerIntent(boolean intent) {
        mOwnerIntent = intent;
    }

    @Override
    public boolean wifiP2pEnabled() {
        return mWifiP2pEnabled;
    }
    @Override
    public void addListener(NetworkListener listener) {
        networkListeners.add(listener);
    }

    @Override
    public void removeListener(NetworkListener listener) {
        networkListeners.remove(listener);
    }

    @Override
    public void removeGroup(final WifiP2pManager.ActionListener actionListener) {
        mManager.removeGroup(mChannel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                actionListener.onSuccess();
            }

            @Override
            public void onFailure(int reason) {
                actionListener.onFailure(reason);
            }
        });
    }

    @Override
    public void discoverPeers(final WifiP2pManager.ActionListener listener) {
        mManager.discoverPeers(mChannel, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                listener.onSuccess();
            }

            @Override
            public void onFailure(int reason) {
                listener.onFailure(reason);
            }
        });
    }

    @Override
    public void stopPeerDiscovery(final WifiP2pManager.ActionListener listener) {
        mManager.stopPeerDiscovery(mChannel, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                listener.onSuccess();
            }

            @Override
            public void onFailure(int reason) {
                listener.onFailure(reason);
            }
        });
    }

    @Override
    public void connect(WifiP2pDevice device, final WifiP2pManager.ActionListener listener) {
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.deviceAddress;
        config.groupOwnerIntent = mOwnerIntent ? 15 : 0;
        mManager.connect(mChannel, config, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                listener.onSuccess();
            }

            @Override
            public void onFailure(int reason) {
                listener.onFailure(reason);
            }
        });
    }

    public void setPeers(WifiP2pDeviceList peers) {
        int connectedTotal = 0;
        for (WifiP2pDevice device : peers.getDeviceList()) {
            if (device.status == WifiP2pDevice.CONNECTED) {
                ++connectedTotal;
            }
        }
        mConnected = connectedTotal;
        if (state == State.Owner) {
            setOwnerIndicator();
        }
        Log.d("peers=%s", peers);
        Iterator<NetworkListener> iterator = networkListeners.iterator();
        while (iterator.hasNext()) {
            NetworkListener listener = iterator.next();
            if (listener == null) {
                iterator.remove();
            } else {
                listener.setPeerList(peers);
            }
        }
    }

    public static int PORT = 8888;

    // client

    private Socket clientSocket;
    private InetAddress groupOwnerAddress;
    private Future<?> clientFuture;

    public void startClient() {
        clientSocket = new Socket();
        try {
            clientSocket.bind(null);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        clientFuture = pool.submit(clientLoop);
    }

    private static final int TIMEOUT = 2000;

    private final Runnable clientLoop = new Runnable() {

        @Override
        public void run() {
            try {
                boolean ok = false;
                while (!ok) {
                    log("connecting");
                    try {
                        clientSocket.connect(new InetSocketAddress(groupOwnerAddress, PORT), TIMEOUT);
                        ok = true;
                    } catch (IOException e) {
                        log("connection refused at %s:%d", groupOwnerAddress.toString(), PORT);
                    }
                    try {
                        Thread.currentThread().sleep(TIMEOUT);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream());
                ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream());
                log("socket connected");
                try {
                    for (;;) {
                        log("waiting for task...");
                        // read task parameters
                        CompParams params = (CompParams) in.readUnshared();
                        int task = in.readInt();
                        log("received task (%d/%d)", task, params.tasks);
                        // compute strip
                        int striph = params.height / params.tasks;
                        int h0 = task * striph;
                        int striph_actual = task == params.tasks - 1 ? params.height - h0 : striph;
                        double[][] strip = Mandelbrot.strip(
                                params.width,
                                params.height,
                                h0,
                                striph_actual,
                                params.subsamples,
                                params.maxiterations
                                );
                        log("sending results for task (%d/%d)", task, params.tasks);
                        // send results
                        out.writeUnshared(strip);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
                log("socket closing");
            } catch (IOException e) {
                e.printStackTrace();
                return;
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    };

    void stopClient() {
        try {
            clientSocket.close();
            try {
                clientFuture.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        clientFuture = null;
        clientSocket = null;
        log("socket closed");
    }

    // server

    private ServerSocket serverSocket;
    private Future<?> serverFuture;

    public void startServer() {
        try {
            log("starting server at port %d", PORT);
            serverSocket = new ServerSocket(PORT);
            serverFuture = pool.submit(serverLoop);
        } catch (IOException e) {
            log("error starting server");
            e.printStackTrace();
            return;
        }
    }

    // only synchronized access allowed
    private Set<Socket> sockets = new HashSet<>();

    public synchronized void addSocket(Socket client) {
        sockets.add(client);
    }

    public synchronized void closeAllSockets() {
        for (Socket socket : sockets) {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        sockets.clear();
    }

    private void log(String format, Object... args) {
        Log.d(format, args);
        if (logInterface == null) return;
        final String s = String.format(format, args);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                logInterface.d(s);
            }
        });
    }

    private final Runnable serverLoop = new Runnable() {
        @Override
        public void run() {
            log("server started");
            try {
                for (;;) {
                    Socket client = serverSocket.accept();
                    log("client connected");
                    addSocket(client);
                }
            } catch (IOException e) {
                log("server stopping");
            }
        }
    };

    public void stopServer() {
        try {
            serverSocket.close();
            try {
                serverFuture.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        closeAllSockets();
        log("server stopped");
    }

    private LogInterface logInterface = null;

    public void setLog(LogInterface log) {
        Log.d("log interface set");
        this.logInterface = log;
        this.logInterface.d("Initialized");
    }

    public enum State {
        Owner,
        Client,
        Disconnected
    }

    private State state = State.Disconnected;

    private int mConnected = 0;
    private boolean mMasterAndConnected = false;

    private void setOwnerIndicator() {
        state = State.Owner;
        indicatorText.setText("Owner (" + mConnected + ")");
        indicatorLight.setBackground(getResources().getDrawable(R.drawable.circle_owner));
    }

    private GroupListener mGroupListener;
    public void setGroupListener(GroupListener groupListener) {
        mGroupListener = groupListener;
        groupListener.setMasterAndConnected(mMasterAndConnected);
    }

    private void notifyGroupListener() {
        if (mGroupListener == null) return;
        mGroupListener.setMasterAndConnected(mMasterAndConnected);
    }

    public void connectionChanged(WifiP2pInfo p2pInfo, NetworkInfo networkInfo) {
        boolean prevMasterAndConnected = mMasterAndConnected;
        mMasterAndConnected = false;
        if (p2pInfo.groupFormed) {
            if (p2pInfo.isGroupOwner) {
                if (state == State.Disconnected) {
                    mMasterAndConnected = true;
                    startServer();
                }
                setOwnerIndicator();
            } else {
                if (state == State.Disconnected) {
                    startClient();
                }
                state = State.Client;
                indicatorText.setText("Client");
                indicatorLight.setBackground(getResources().getDrawable(R.drawable.circle_client));
                groupOwnerAddress = p2pInfo.groupOwnerAddress;
            }
        } else {
            if (state == State.Owner) {
                stopServer();
            } else if (state == State.Client) {
                stopClient();
            }
            state = State.Disconnected;
            indicatorText.setText("Disconnected");
            indicatorLight.setBackground(getResources().getDrawable(R.drawable.circle));
        }
        if (mMasterAndConnected != prevMasterAndConnected) {
            notifyGroupListener();
        }
        Iterator<NetworkListener> iterator = networkListeners.iterator();
        while (iterator.hasNext()) {
            NetworkListener listener = iterator.next();
            if (listener == null) {
                iterator.remove();
            } else {
                listener.discoveryStopped();
                listener.wifiP2pEnabled(!p2pInfo.groupFormed);
            }
        }
    }

    // master computation task
    private Future<?> computationTask = null;

    // tasks write to this array in parallel (in different elements)
    private double[][] image;

    // only synchronized access allowed
    // sockets are transferred to this set when they are given a computation task
    private Map<Socket, Future<?>> computationSockets = new HashMap<>();

    public void startComputation() {
        if (computationTask != null) return;
        final CompParams params = mGroupListener.getCompParams();
        computationTask = pool.submit(new Runnable() {
            @Override
            public void run() {
                log("computation started");
                image = new double[params.height][params.width];
                try {
                    // launch all tasks
                    for (int task = 0; task < params.tasks; ++task) {
                        launchTask(params, task);
                    }
                    // wait for all tasks to finish
                    waitTasks();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        });
    }

    private void waitTasks() throws InterruptedException {
        synchronized (poolMonitor) {
            while (!computationSockets.isEmpty()) {
                poolMonitor.wait();
            }
        }
    }

    private Object poolMonitor = new Object();

    // get next available connected socket
    private synchronized Socket detachSocket() {
        log("sockets.isEmpty=%b", sockets.isEmpty());
        Iterator<Socket> it = sockets.iterator();
        if (!it.hasNext()) return null;
        Socket socket = it.next();
        it.remove();
        return socket;
    }

    // call only when poolMonitor is held
    private Socket getDetachedSocket() throws InterruptedException {
        Socket socket = null;
        while ((socket = detachSocket()) == null) {
            poolMonitor.wait();
        }
        return socket;
    }

    // called from per-client connection thread
    private void reattachSocket(Socket socket) {
        synchronized (poolMonitor) {
            computationSockets.remove(socket);
            synchronized (this) {
                sockets.add(socket);
            }
            poolMonitor.notify();
        }
    }

    // run from the main computation thread
    private void launchTask(final CompParams params, final int task) throws InterruptedException {
        synchronized (poolMonitor) {
            final Socket socket = getDetachedSocket();
            Future<?> future = pool.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                        ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
                        // write task parameters
                        log("sending out task (%d/%d)", task, params.tasks);
                        out.writeUnshared(params);
                        out.writeInt(task);
                        // read result
                        log("waiting results for task (%d/%d)...", task, params.tasks);
                        double[][] strip = (double[][]) in.readUnshared();
                        // merge result
                        log("merging results for task (%d/%d)", task, params.tasks);
                        int striph = params.height / params.tasks;
                        int h0 = task * striph;
                        int striph_actual = task == params.tasks - 1 ? params.height - h0 : striph;
                        for (int y = 0; y < striph_actual; ++y) {
                            for (int x = 0; x < params.width; ++x) {
                                image[y + h0][x] = strip[y][x];
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                    }
                    // return socket to available socket list
                    reattachSocket(socket);
                    log("task (%d/%d) done", task, params.tasks);
                }
            });
            computationSockets.put(socket, future);
        }
    }
}
