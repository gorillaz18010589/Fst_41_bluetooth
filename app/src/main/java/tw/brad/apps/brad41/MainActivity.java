package tw.brad.apps.brad41;
//藍芽用在短距離大約70公尺,還有更短的式NFC公車刷悠遊卡
//RFID:NFC延伸出來用於工廠上面,東西一拿出倉庫馬上被顯示出貨
//Beacon:近距離路過某個店家,突然收到他的訊息,針策擬以靠近的範圍去發送訊息,屬於室內定位,空間的三點可以偵測你的位置
//iphone也有IBeacon可以偵測室內定位
//bluetooth藍芽:1.配對行為向耳機配對2.配玩隊在手機放資料
//3.藍芽二代有一個簡單的安全配對(ssp)比較舊,距離50~70公尺左右,主要是資訊溝通
//4.藍芽三代:速度有加快,增強電源控制,實際空間供耗較低了,大部分用在車載系統
//5.藍芽四代:節能省電,要賣歐洲要通過檢驗,主要式交換資料,雙方交換,其中一個連網把資料傳上網路
//6.藍芽五代:距離可以到300公尺,傳輸速率提供8倍,但目前一般手機都沒到
//手機要4.3以前才有支援BLE,再來手機也要看有沒有BLE才能支援,不然只有傳統藍芽

//<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION"/>存取位置
//<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>存取精確位置
//<uses-permission android:name="android.permission.BLUETOOTH"/>藍芽權限
//<uses-permission android:name="android.permission.BLUETOOTH_ADMIN"/>

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleAdapter;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private BluetoothAdapter bluetoothAdapter;
    private LinkedList<HashMap<String,Object>> devices = new LinkedList<>();
    private ListView listDevices;
    private SimpleAdapter adapter;
    private String[] from = {"name", "mac","device"};
    private int[] to = {R.id.item_name, R.id.item_mac};
    private final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    //3.準備接收廣播訊息
    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            try {
                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    String deviceName = device.getName(); //取得藍芽名字
                    String deviceHardwareAddress = device.getAddress(); // 取得藍芽位置
                    Log.v("brad", deviceName);

                    HashMap<String,Object> deviceData = new HashMap<>();
                    deviceData.put("name", deviceName);//掛上name參數
                    deviceData.put("mac", deviceHardwareAddress);
                    deviceData.put("device", device);

                    //尋訪devices
                    boolean isRepeat = false;
                    for (HashMap<String,Object> dd : devices){
                        if (dd.get("device") == device){//如果挖到地址一樣的
                            Log.v("brad", "device dup");
                            isRepeat = true; //給他true
                            break;
                        }
                    }

                    if (!isRepeat) {
                        Log.v("brad", "new device add");
                        devices.add(deviceData);//新增資料
                        adapter.notifyDataSetChanged();//調變器啟動
                    }

                }
            }catch (Exception e){

            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //設定藍芽權限設定
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)  //詢問藍芽位置存取權限
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},//要藍雅位置權限
                    123);
        }else{
            init();
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        init();
    }
    //1.初始化設定
    private void  init(){
        listDevices = findViewById(R.id.listDevices); //抓到listview的畫面
        adapter = new SimpleAdapter(this,devices, R.layout.item_device,from,to);//1.創一個簡單調變器從這頁面 2.物件 3.指定畫面,4.從mac的資料 等 5.to灌到頁面
        listDevices.setAdapter(adapter); //設置調變器
        listDevices.setOnItemClickListener(new AdapterView.OnItemClickListener() { //當按下list view的item
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                doPair(i);
            }
        });

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter(); //取得我們這方的藍芽
        if (!bluetoothAdapter.isEnabled()) {//如果藍芽馬達,沒有啟動
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE); //連接(蘭雅啟動)
            startActivityForResult(enableBtIntent, 123); //啟動藍芽
        }else{
            regReceiver();  //連接廣播
        }
    }

    //4.如果有允許權限才會做以下的式
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 123 && resultCode == RESULT_OK){ //如果你的123詢係依值跑來這,而且權限ok
            regReceiver(); //連接廣播
        }
    }
    //5.註冊廣播
    private void regReceiver(){
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(broadcastReceiver, filter);//連接廣播
    }

    //6.關掉時取消藍芽
    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(broadcastReceiver);//關掉廣播
    }
    //2.按下按鈕查詢以配對過的藍芽
    public void test1(View view) {
        Set<BluetoothDevice> pairedDevices = //BluetoothDevice 代表別人的藍芽
                bluetoothAdapter.getBondedDevices();//bluetoothAdapter我的藍芽紀錄

        if (pairedDevices.size() > 0) {//如果藍芽記錄的長度大於0
            // There are paired devices. Get the name and address of each paired device.
            for (BluetoothDevice device : pairedDevices) {
                String deviceName = device.getName();  //尋訪別人手機的資訊,取得連過的手機名
                String deviceHardwareAddress = device.getAddress();//尋訪別人手機的資訊,取得連過的藍芽位置

                Log.v("brad", deviceName + ":" + deviceHardwareAddress);

            }
        }
    }

   // 7.藍芽搜尋
    public void test2(View view){
        if (!bluetoothAdapter.isDiscovering()) {
            devices.clear();
            bluetoothAdapter.startDiscovery();
            Log.v("brad","搜尋中...");
        }
    }

    //藍芽被人搜尋300秒
    public void test4(View view){
        Intent discoverableIntent =
                new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);  //被搜尋回應
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
        startActivity(discoverableIntent);//啟動被搜尋藍雅的intent
        Log.v("brad","藍芽被搜尋中啟動");
    }


    //8.如果藍芽正在搜尋,藍芽關閉搜尋
    public void test3(View view){
        if (bluetoothAdapter.isDiscovering()){ //如果藍雅正在搜尋的話
            Log.v("brad", "關閉搜尋中...");
            bluetoothAdapter.cancelDiscovery();//關掉藍芽
        }
    }

    //10.啟動藍雅配對
    private void doPair(int i){
        // i => MAC device
        //new AcceptThread().start();
        BluetoothDevice device = (BluetoothDevice) devices.get(i).get("device");//取得所按的item(i)跟取得device,轉型成藍雅
        new ConnectThread(device).start(); //開始主動配對的執行緒(此藍芽)

    }

    //9.藍芽主動配對
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            // Use a temporary object that is later assigned to mmSocket
            // because mmSocket is final.
            BluetoothSocket tmp = null;
            mmDevice = device;

            try {
                // Get a BluetoothSocket to connect with the given BluetoothDevice.
                // MY_UUID is the app's UUID string, also used in the server code.
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
                Log.v("brad", "Socket's create() method failed", e);
            }
            mmSocket = tmp;
        }

        public void run() {
            // Cancel discovery because it otherwise slows down the connection.
            bluetoothAdapter.cancelDiscovery();

            try {
                // Connect to the remote device through the socket. This call blocks
                // until it succeeds or throws an exception.
                mmSocket.connect(); //藍雅連線成功
                Log.v("brad", "connect OK");
            } catch (IOException connectException) {
                // Unable to connect; close the socket and return.
                try {
                    mmSocket.close();//藍芽關閉
                } catch (IOException closeException) {
                    Log.v("brad", "Could not close the client socket", closeException);
                }
                return;
            }

            // The connection attempt succeeded. Perform work associated with
            // the connection in a separate thread.
            //manageMyConnectedSocket(mmSocket);
        }

        // Closes the client socket and causes the thread to finish.
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.v("brad", "Could not close the client socket", e);
            }
        }
    }

    //藍雅接收被配對
    private class AcceptThread extends Thread {
        private final BluetoothServerSocket mmServerSocket;

        public AcceptThread() {
            // Use a temporary object that is later assigned to mmServerSocket
            // because mmServerSocket is final.
            BluetoothServerSocket tmp = null;
            try {
                // MY_UUID is the app's UUID string, also used by the client code.
                tmp = bluetoothAdapter.listenUsingRfcommWithServiceRecord("brad", MY_UUID);
            } catch (IOException e) {
                Log.v("brad", "Socket's listen() method failed", e);
            }
            mmServerSocket = tmp;
        }

        public void run() {
            BluetoothSocket socket = null;
            // Keep listening until exception occurs or a socket is returned.
            while (true) {
                try {
                    socket = mmServerSocket.accept(); //藍芽被接收中
                } catch (IOException e) {
                    Log.v("brad", "Socket's accept() method failed", e);
                    break;
                }

                if (socket != null) {
                    // A connection was accepted. Perform work associated with
                    // the connection in a separate thread.
                    //manageMyConnectedSocket(socket);
                    try {
                        mmServerSocket.close();
                    }catch (Exception e){

                    }finally {
                        break;
                    }
                }
            }
        }

        // Closes the connect socket and causes the thread to finish.
        public void cancel() {
            try {
                mmServerSocket.close();
            } catch (IOException e) {
                Log.v("brad", "Could not close the connect socket", e);
            }
        }
    }



}