package com.duoda.sell.mqtt;

import com.facebook.react.bridge.*;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;


/**
 * Created by januslo on 2017/4/6.
 */
public class MQTTClientModule extends ReactContextBaseJavaModule {
    private static final String TAG = "MQTTClient";
    private static final SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmmSSS");
    public static final String SERVER_URI = "serverUri";
    public static final String STRING_EMPTY = "";
    public static final String CLIENT_ID = "clientId";
    public static final String TOPIC = "topic";
    public static final String QOS = "qos";
    public static final int INT_ZERO = 0;
    public static final String CLEAR_SESSION = "clearSession";
    public static final String PASSWORD = "password";
    public static final String USERNAME = "username";
    public static final String TIMEOUT = "timeout";
    public static final String VERSION = "version";
    public static final String WILL = "will";
    public static final String PAYLOAD = "payload";
    public static final String RETAINED = "retained";
    public static final String KEY_ALIVE_INTERVAL = "keyAliveInterval";
    public static final String EVENT_ERROR="mqttClientError";
    public static final String EVENT_MESSAGE_ARRIVED="mqttClientMessageArrived";
    private static Map<String, MqttClient> clients=new HashMap<String, MqttClient>();

    @Override
    public String getName() {
        return TAG;
    }

    public MQTTClientModule(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @ReactMethod
    public void subscribe(ReadableMap options) {
        String serverURI = options.hasKey(SERVER_URI) ? options.getString(SERVER_URI) : STRING_EMPTY;
        if (!isNotEmpty(serverURI)) {
            WritableMap params = Arguments.createMap();
            params.putString("error","serviceUri is required.");
            sendEvent(getReactApplicationContext(),EVENT_ERROR,params);
        }
        String clientId = options.hasKey(CLIENT_ID) ? options.getString(CLIENT_ID) : STRING_EMPTY;
        if (!isNotEmpty(clientId)) {
            clientId = format.format(new Date()) + new Random().nextInt();
        }
        String topic = options.hasKey(TOPIC) ? options.getString(TOPIC) : STRING_EMPTY;
        int qos = options.hasKey(QOS) ? options.getInt(QOS) : INT_ZERO;
        boolean clearSession = options.hasKey(CLEAR_SESSION) ? options.getBoolean(CLEAR_SESSION) : false;
        String password = options.hasKey(PASSWORD) ? options.getString(PASSWORD) : STRING_EMPTY;
        String username = options.hasKey(USERNAME) ? options.getString(USERNAME) : STRING_EMPTY;
        int timeout = options.hasKey(TIMEOUT) ? options.getInt(TIMEOUT) : MqttConnectOptions.CONNECTION_TIMEOUT_DEFAULT;
        int version = options.hasKey(VERSION) ? options.getInt(VERSION) : MqttConnectOptions.MQTT_VERSION_DEFAULT;
        int keepAliveInterval = options.hasKey(KEY_ALIVE_INTERVAL)?options.getInt(KEY_ALIVE_INTERVAL):MqttConnectOptions.KEEP_ALIVE_INTERVAL_DEFAULT;
        ReadableMap will = options.hasKey(WILL) ? options.getMap(WILL) : null;

        MqttClient client = null;
        if(clients.containsKey(clientId) && clients.get(clientId)!=null){
            client = clients.get(clientId);
        }
        if(client==null || !client.isConnected()){
            try {
                client = new MqttClient(serverURI, clientId,new MemoryPersistence());
                clients.put(clientId,client);
                MqttConnectOptions opts = new MqttConnectOptions();
                opts.setCleanSession(clearSession);
                if (isNotEmpty(password)) {
                    opts.setPassword(password.toCharArray());
                }
                if (isNotEmpty(username)) {
                    opts.setUserName(username);
                }
                if (timeout >= 0) {// zero is the default value in this plugin.
                    opts.setConnectionTimeout(timeout);
                }
                if (version >= 0) {
                    opts.setMqttVersion(version);
                }
                if (will != null) {
                    String will_topic = will.hasKey(TOPIC)? will.getString(TOPIC):STRING_EMPTY;
                    String payload = will.hasKey(PAYLOAD)?will.getString(PAYLOAD):STRING_EMPTY;
                    int will_qos = will.hasKey(QOS)?will.getInt(QOS):INT_ZERO;
                    boolean retained = will.hasKey(RETAINED)?will.getBoolean(RETAINED):false;
                    opts.setWill(will_topic,payload.getBytes(),will_qos,retained);
                }

                opts.setKeepAliveInterval(keepAliveInterval);
                bindCallback(client);
                client.connect(opts);
                client.subscribe(topic, qos);

            } catch (Exception e) {
                e.printStackTrace();
                WritableMap params = Arguments.createMap();
                params.putString("error",e.getMessage());
                sendEvent(getReactApplicationContext(),EVENT_ERROR,params);
            }finally{
                if(client!=null){
                    try {
                        client.close();
                    }catch (Throwable t){
                        //ignore.
                    }
                }
            }
         }else{
            // renew callback application context.
            bindCallback(client);
        }

    }

    private void bindCallback(MqttClient client) {
        client.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                WritableMap params = Arguments.createMap();
                params.putString("error","error connectionLost:"+cause.getMessage());
                sendEvent(getReactApplicationContext(),EVENT_ERROR,params);
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                WritableMap params = Arguments.createMap();
                params.putString("topic",topic);
                params.putString("payload",new String(message.getPayload(), Charset.forName("UTF-8")));
                params.putString("qos",String.valueOf(message.getQos()));
                sendEvent(getReactApplicationContext(),EVENT_MESSAGE_ARRIVED,params);
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                // do nothing...
            }
        });
    }

    private boolean isNotEmpty(String arg) {
        return arg != null && arg.length() > 0;
    }
    private void sendEvent(ReactContext reactContext,
                           String eventName,
                           WritableMap params) {
        reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);
    }
}
