package com.example.mygcs2;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.PointF;
import android.location.Location;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;

import com.naver.maps.geometry.LatLng;
import com.naver.maps.geometry.LatLngBounds;
import com.naver.maps.map.CameraPosition;
import com.naver.maps.map.CameraUpdate;
import com.naver.maps.map.LocationTrackingMode;
import com.naver.maps.map.MapFragment;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.OnMapReadyCallback;
import com.naver.maps.map.UiSettings;
import com.naver.maps.map.overlay.InfoWindow;
import com.naver.maps.map.overlay.Marker;
import com.naver.maps.map.overlay.OverlayImage;
import com.naver.maps.map.overlay.PathOverlay;
import com.naver.maps.map.overlay.PolygonOverlay;
import com.naver.maps.map.util.FusedLocationSource;
import com.naver.maps.map.util.MarkerIcons;
import com.o3dr.android.client.ControlTower;
import com.o3dr.android.client.Drone;
import com.o3dr.android.client.apis.ControlApi;
import com.o3dr.android.client.apis.VehicleApi;
import com.o3dr.android.client.interfaces.DroneListener;
import com.o3dr.android.client.interfaces.LinkListener;
import com.o3dr.android.client.interfaces.TowerListener;
import com.o3dr.services.android.lib.coordinate.LatLong;
import com.o3dr.services.android.lib.drone.attribute.AttributeEvent;
import com.o3dr.services.android.lib.drone.attribute.AttributeType;
import com.o3dr.services.android.lib.drone.companion.solo.SoloAttributes;
import com.o3dr.services.android.lib.drone.companion.solo.SoloState;
import com.o3dr.services.android.lib.drone.connection.ConnectionParameter;
import com.o3dr.services.android.lib.drone.property.Altitude;
import com.o3dr.services.android.lib.drone.property.Attitude;
import com.o3dr.services.android.lib.drone.property.Battery;
import com.o3dr.services.android.lib.drone.property.Gps;
import com.o3dr.services.android.lib.drone.property.Speed;
import com.o3dr.services.android.lib.drone.property.State;
import com.o3dr.services.android.lib.drone.property.Type;
import com.o3dr.services.android.lib.drone.property.VehicleMode;
import com.o3dr.services.android.lib.gcs.link.LinkConnectionStatus;
import com.o3dr.services.android.lib.model.AbstractCommandListener;
import com.o3dr.services.android.lib.model.SimpleCommandListener;

import org.droidplanner.services.android.impl.core.helpers.geoTools.GeoTools;
import org.droidplanner.services.android.impl.core.helpers.geoTools.LineLatLong;
import org.droidplanner.services.android.impl.core.polygon.Polygon;
import org.droidplanner.services.android.impl.core.survey.grid.Trimmer;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static com.example.mygcs2.Values.*;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback,
        DroneListener, TowerListener, LinkListener {
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1000;
    private FusedLocationSource locationSource;
    private NaverMap naverMap;
    private int DEFAULT_ZOOM_LEVEL = 17;
    LatLng DEFAULT_LATLNG = new LatLng(35.9436,126.6842);
    private static final String TAG = MainActivity.class.getSimpleName();

    private Drone drone;
    private int droneType = Type.TYPE_UNKNOWN;
    private ControlTower controlTower;
    private final Handler handler = new Handler();

    private Spinner modeSelector;
    private Marker droneMarker = new Marker();
    List<Marker> polyMarkers = new ArrayList<>();
    List<LatLng> polyMarkersLatLng = new ArrayList<>();
    ArrayList<PointF> polyPointFs = new ArrayList<>();
    PolygonOverlay polygon = new PolygonOverlay();
    Marker marker_goal = new Marker(); // Guided 모드 마커
    int testCount = 0;
    int pathBoundDiagonalType;
    int pathDirection;

    PolygonOverlay polygonOverlay = new PolygonOverlay();
    List<LatLng> latLngsTmp = new ArrayList<>();

    PathOverlay path = new PathOverlay();
    List<LatLng> dronePathCoords = new ArrayList<>();
    private boolean isMapLinked = false;
    private boolean isCameraLocked = true;
    private boolean isPolygonMissionEnabled = false;

    private double droneMissionAlt = 1;

    ArrayList<String> recycler_list = new ArrayList<>();

    Marker tmpMarker1 = new Marker();
    Marker tmpMarker2 = new Marker();
    Marker tmpMarker3 = new Marker();
    Marker tmpMarker4 = new Marker();
    Marker tmpMarker5 = new Marker();
    Marker tmpMarker6 = new Marker();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        hideSystemUI();

        final Context context = getApplicationContext();
        this.controlTower = new ControlTower(context);
        this.drone = new Drone(context);

        this.modeSelector = (Spinner) findViewById(R.id.modeSelect);
        this.modeSelector.setOnItemSelectedListener(new Spinner.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                onFlightModeSelected(view);
                hideSystemUI();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                hideSystemUI();
            }
        });

        // ↓ map sync
        FragmentManager fm = getSupportFragmentManager();
        MapFragment mapFragment = (MapFragment)fm.findFragmentById(R.id.map);
        if (mapFragment == null) {
            mapFragment = MapFragment.newInstance();
            fm.beginTransaction().add(R.id.map, mapFragment).commit();
        }
        mapFragment.getMapAsync(this);
        locationSource =
                new FusedLocationSource(this, LOCATION_PERMISSION_REQUEST_CODE);

        initLayout();
    }

    public void test_btn(View view) {
        sendRecyclerMessage(String.format("~~~~~~아주~~~~~~~~~~~~긴~~~~~~~~~~~~~메세지~~~~~~~~"));
        getDronePolyPath(polygon, TMP_DISTANCE,0);
    }

    public void btnclearRecycler(View view) {
        clearRecyclerMessage();
    }

    private void sendRecyclerMessage(String message) {
        String localTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        recycler_list.add(String.format("[" + localTime + "]" + message));
        refreshRecyclerView();
    }

    public void clearRecyclerMessage(){
        recycler_list.clear();
        refreshRecyclerView();
    }

    private void refreshRecyclerView() {
        // 리사이클러뷰에 LinearLayoutManager 객체 지정.
        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // 리사이클러뷰에 SimpleAdapter 객체 지정.
        SimpleTextAdapter adapter = new SimpleTextAdapter(recycler_list);
        recyclerView.setAdapter(adapter);

        recyclerView.scrollToPosition(recycler_list.size()-1);
        //recyclerView.smoothScrollToPosition(recycler_list.size()-1);
    }

    @Override
    public void onStart() {
        super.onStart();
        this.controlTower.connect(this);
        updateVehicleModesForType(this.droneType);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (this.drone.isConnected()) {
            this.drone.disconnect();
            updateConnectedButton(false);
        }

        this.controlTower.unregisterDrone(this.drone);
        this.controlTower.disconnect();
    }

    @Override
    public void onTowerConnected() {
        alertUser("DroneKit-Android Connected");
        this.controlTower.registerDrone(this.drone, this.handler);
        this.drone.registerDroneListener(this);
    }

    @Override
    public void onTowerDisconnected() {
        alertUser("DroneKit-Android Interrupted");
    }

    @Override
    public void onDroneEvent(String event, Bundle extras) {
        switch (event) {
            case AttributeEvent.STATE_CONNECTED:
                alertUser("Drone Connected");
                updateConnectedButton(this.drone.isConnected());
                updateArmButton();
                checkSoloState();
                break;

            case AttributeEvent.STATE_DISCONNECTED:
                alertUser("Drone Disconnected");
                updateConnectedButton(this.drone.isConnected());
                updateArmButton();
                break;

            case AttributeEvent.STATE_UPDATED:
            case AttributeEvent.STATE_ARMING:
                updateArmButton();
                break;

            case AttributeEvent.TYPE_UPDATED:
                Type newDroneType = this.drone.getAttribute(AttributeType.TYPE);
                if (newDroneType.getDroneType() != this.droneType) {
                    this.droneType = newDroneType.getDroneType();
                    updateVehicleModesForType(this.droneType);
                }
                break;

            case AttributeEvent.STATE_VEHICLE_MODE:
                updateVehicleMode();
                break;

            case AttributeEvent.SPEED_UPDATED:
                updateSpeed();
                break;

            case AttributeEvent.ALTITUDE_UPDATED:
                updateAltitude();
                break;

            case AttributeEvent.HOME_UPDATED:
                //TODO 사용하지 않는 코드 (사용자와의 거리) 001
                //updateDistanceFromHome();
                break;

            case AttributeEvent.BATTERY_UPDATED:
                updateBattery();
                break;

            case AttributeEvent.ATTITUDE_UPDATED:
                updateYAW();
                break;

            case AttributeEvent.GPS_COUNT:
                updateSatellite();
                break;

            case AttributeEvent.GPS_POSITION:
                updateDroneMarker();
                updateDronePath();
                break;

            case AttributeEvent.AUTOPILOT_MESSAGE:
                Bundle bundle = extras;
                String msg = extras.getString("com.o3dr.services.android.lib.attribute.event.extra.AUTOPILOT_MESSAGE");
                alertUser("[AUTOPILOT_MESSAGE]" + msg);
                break;

            default:
                break;
        }
    }

    private void checkSoloState() {
        final SoloState soloState = drone.getAttribute(SoloAttributes.SOLO_STATE);
        if (soloState == null){
            alertUser("Unable to retrieve the solo state.");
        }
        else {
            alertUser("Solo state is up to date.");
        }
    }

    @Override
    public void onDroneServiceInterrupted(String errorMsg) {

    }

    public void onBtnConnectTap(View view) {
        if (this.drone.isConnected())
        {
            this.drone.disconnect();
        }
        else
        {
            ConnectionParameter connectionParams = ConnectionParameter.newUdpConnection(null);
            this.drone.connect(connectionParams);
        }
    }

    public void onFlightModeSelected(View view) {
        VehicleMode vehicleMode = (VehicleMode) this.modeSelector.getSelectedItem();

        VehicleApi.getApi(this.drone).setVehicleMode(vehicleMode, new AbstractCommandListener() {
            @Override
            public void onSuccess() {
                alertUser("Vehicle mode change successful.");
            }

            @Override
            public void onError(int executionError) {
                alertUser("Vehicle mode change failed: " + executionError);
            }

            @Override
            public void onTimeout() {
                alertUser("Vehicle mode change timed out.");
            }
        });
    }

    public void onArmButtonTap(View view) {
        State vehicleState = this.drone.getAttribute(AttributeType.STATE);

        if (vehicleState.isFlying()) {
            // Land
            VehicleApi.getApi(this.drone).setVehicleMode(VehicleMode.COPTER_LAND, new SimpleCommandListener() {
                @Override
                public void onError(int executionError) {
                    alertUser("Unable to land the vehicle.");
                }

                @Override
                public void onTimeout() {
                    alertUser("Unable to land the vehicle.");
                }
            });
        } else if (vehicleState.isArmed()) {
            // Take off
            ControlApi.getApi(this.drone).takeoff(droneMissionAlt, new AbstractCommandListener() {

                @Override
                public void onSuccess() {
                    alertUser("Taking off...");
                }

                @Override
                public void onError(int i) {
                    alertUser("Unable to take off.");
                }

                @Override
                public void onTimeout() {
                    alertUser("Unable to take off.");
                }
            });
        } else if (!vehicleState.isConnected()) {
            // Connect
            alertUser("Connect to a drone first");
        } else {
            // Connected but not Armed
            VehicleApi.getApi(this.drone).arm(true, false, new SimpleCommandListener() {
                @Override
                public void onError(int executionError) {
                    alertUser("Unable to arm vehicle.");
                }

                @Override
                public void onTimeout() {
                    alertUser("Arming operation timed out.");
                }
            });
        }
    }

    protected void updateConnectedButton(Boolean isConnected) {
        Button connectButton = (Button) findViewById(R.id.btnConnect);
        if (isConnected) {
            connectButton.setText("Disconnect");
        } else {
            connectButton.setText("Connect");
        }
    }

    protected void updateArmButton() {
        State vehicleState = this.drone.getAttribute(AttributeType.STATE);
        Button armButton = (Button) findViewById(R.id.btnArmTakeOff);

        if (!this.drone.isConnected()) {
            armButton.setVisibility(View.INVISIBLE);
        } else {
            armButton.setVisibility(View.VISIBLE);
        }

        if (vehicleState.isFlying()) {
            // Land
            armButton.setText("LAND");
        } else if (vehicleState.isArmed()) {
            // Take off
            armButton.setText("TAKE OFF");
        } else if (vehicleState.isConnected()) {
            // Connected but not Armed
            armButton.setText("ARM");
        }
    }

    protected void updateAltitude() {
        TextView altitudeTextView = (TextView) findViewById(R.id.altitudeValueTextView);
        Altitude droneAltitude = this.drone.getAttribute(AttributeType.ALTITUDE);
        altitudeTextView.setText(String.format("고도: %3.1f", droneAltitude.getAltitude()) + "m");
    }

    protected void updateSpeed() {
        TextView speedTextView = (TextView) findViewById(R.id.speedValueTextView);
        Speed droneSpeed = this.drone.getAttribute(AttributeType.SPEED);
        speedTextView.setText(String.format("속도: %3.1f", droneSpeed.getGroundSpeed()) + "m/s");
    }

    protected void updateVehicleModesForType(int droneType) {
        List<VehicleMode> vehicleModes = VehicleMode.getVehicleModePerDroneType(droneType);
        ArrayAdapter<VehicleMode> vehicleModeArrayAdapter = new ArrayAdapter<VehicleMode>(this, R.layout.spinner_item, vehicleModes);
        vehicleModeArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        this.modeSelector.setAdapter(vehicleModeArrayAdapter);
    }

    protected void updateVehicleMode() {
        State vehicleState = this.drone.getAttribute(AttributeType.STATE);
        VehicleMode vehicleMode = vehicleState.getVehicleMode();
        ArrayAdapter arrayAdapter = (ArrayAdapter) this.modeSelector.getAdapter();
        this.modeSelector.setSelection(arrayAdapter.getPosition(vehicleMode));
    }

    protected void updateBattery(){
        TextView batteryTextView = (TextView) findViewById(R.id.batteryBalueTextView);
        Battery droneBattery = this.drone.getAttribute(AttributeType.BATTERY);
        batteryTextView.setText(String.format("전압: %3.1f", droneBattery.getBatteryVoltage()) + "v");
    }

    protected void updateYAW(){
        TextView droneYAWTextView = (TextView) findViewById(R.id.YAWTextView);
        Attitude droneAttitude = this.drone.getAttribute(AttributeType.ATTITUDE);
        double droneYaW = droneAttitude.getYaw();
        droneYAWTextView.setText(String.format("YAW: %3.1f", droneYaW) + "deg");

        droneMarker.setAngle((float)(droneYaW + 360));
    }

    protected void updateSatellite(){
        TextView droneSatellite = (TextView) findViewById(R.id.satelliteTextView);
        Gps droneGPS = this.drone.getAttribute(AttributeType.GPS);
        droneSatellite.setText(String.format("위성: %d", droneGPS.getSatellitesCount()));
    }

    protected void updateDroneMarker(){
        LatLng currentLatlngLocation = getCurrentLatLng();
        droneMarker.setPosition(currentLatlngLocation);
        droneMarker.setMap(naverMap);

        if(isCameraLocked){
            CameraUpdate cameraUpdate = CameraUpdate.scrollTo(currentLatlngLocation);
            naverMap.moveCamera(cameraUpdate);
        }
    }



    protected void updateDronePath(){
        dronePathCoords.add(getCurrentLatLng());
        path.setCoords(dronePathCoords);
    }

    protected LatLong getCurrentLatLong(){
        Gps gps = this.drone.getAttribute(AttributeType.GPS);
        return gps.getPosition();
    }

    //TODO gps수신이 불량할 경우 NullPointerException발생, 이경우 default LatLong을 어떻게?? 임시로 (0,0)으로 넣어놓음
    protected LatLng getCurrentLatLng(){
        LatLng currentLatlngLocation = new LatLng(0,0);

        try {
            LatLong currentLatlongLocation = getCurrentLatLong();
            currentLatlngLocation = new LatLng(currentLatlongLocation.getLatitude(),currentLatlongLocation.getLongitude());

        }
        catch(NullPointerException e) {
            sendRecyclerMessage("GPS 수신이 불안정 합니다.");

        }

        return currentLatlngLocation;
    }

    protected void alertUser(String message) {
        sendRecyclerMessage(message);
    }

    @Override
    public void onLinkStateUpdated(@NonNull LinkConnectionStatus connectionStatus) {
        switch(connectionStatus.getStatusCode()){
            case LinkConnectionStatus.FAILED:
                Bundle extras = connectionStatus.getExtras();
                String msg = null;
                if (extras != null) {
                    msg = extras.getString(LinkConnectionStatus.EXTRA_ERROR_MSG);
                }
                alertUser("Connection Failed:" + msg);
                break;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,  @NonNull int[] grantResults) {
        if (locationSource.onRequestPermissionsResult(
                requestCode, permissions, grantResults)) {
            if (!locationSource.isActivated()) { // 권한 거부됨
                naverMap.setLocationTrackingMode(LocationTrackingMode.None);
            }
            return;
        }
        super.onRequestPermissionsResult(
                requestCode, permissions, grantResults);
    }

    @Override
    public void onMapReady(@NonNull NaverMap naverMap) {
        this.naverMap = naverMap;
        naverMap.setLocationSource(locationSource);

        naverMap.setOnMapClickListener(new NaverMap.OnMapClickListener() {
            @Override
            public void onMapClick(@NonNull PointF pointF, @NonNull LatLng latLng) {
                if(isPolygonMissionEnabled) {
                    makePolygon(latLng);
                }
            }
        });

        naverMap.addOnCameraChangeListener(new NaverMap.OnCameraChangeListener() {
            @Override
            public void onCameraChange(int i, boolean b) {
            }
        });

        naverMap.addOnLocationChangeListener(new NaverMap.OnLocationChangeListener() {
            @Override
            public void onLocationChange(@NonNull Location location) {
                sendRecyclerMessage(location.getLatitude() + ", " + location.getLongitude());
            }
        });

        naverMap.setOnMapLongClickListener(new NaverMap.OnMapLongClickListener() {
                                               @Override
                                               public void onMapLongClick(@NonNull PointF pointF, @NonNull LatLng coord) {
                                                   LongClickWarning(pointF, coord);
                                               }
                                           });

        initMap();
    }

    public void getDronePolyPath(PolygonOverlay polygon, int distance, float angle, int startPoint){
        LatLng boundNE =polygon.getBounds().getNorthEast();
        LatLng boundNW =polygon.getBounds().getNorthWest();
        LatLng boundSE =polygon.getBounds().getSouthEast();
        LatLng boundSW =polygon.getBounds().getSouthWest();

        polygon.setMap(null);

        //TODO 지울것) 테두리 그리기
        latLngsTmp.clear();
        latLngsTmp.add(boundNE);
        latLngsTmp.add(boundNW);
        latLngsTmp.add(boundSW);
        latLngsTmp.add(boundSE);
        polygonOverlay.setCoords(latLngsTmp);
        polygonOverlay.setColor(Color.TRANSPARENT);
        polygonOverlay.setOutlineWidth(3);
        polygonOverlay.setMap(naverMap);


        List<LatLng> targetLatLngs = new ArrayList<>();

        if(startPoint == START_POINT_NEAREST){
            sendRecyclerMessage("가장 가까운 꼭지점에서 비행을 시작합니다.");

            //1.가장 가까운 점 구하기
            LatLng currentDronePosition = getCurrentLatLng();
            //TODO 지워야하는 임시좌표
            currentDronePosition = new LatLng(35.9436,126.6842);
            Marker marker = new Marker(currentDronePosition);
            marker.setWidth(50);
            marker.setHeight(50);
            marker.setCaptionText("드론");
            marker.setMap(naverMap);

            List<LatLng> polygonVertexes = polygon.getCoords();
            List<Double> distanceToVertexes = new ArrayList<>();

            //1-1.모든 꼭지점과 현 드론위치사이 거리 등록
            for(LatLng latLng : polygonVertexes){
                distanceToVertexes.add(latLng.distanceTo(currentDronePosition));
            }
            //1-2.최소거리추출 → 시작점
            int minInedx = distanceToVertexes.indexOf(Collections.min(distanceToVertexes));
            LatLng firstPoint = polygonVertexes.get(minInedx);
            targetLatLngs.add(firstPoint);

            //2.그 다음 점 구하기
            double distanceToRight = 0;
            double distanceToLeft = 0;
            int rNeghborInedx;
            int lNeghborInedx;
            LatLng secondPoint;
            //2-1.시작점 양쪽과의 거리를 구하여 긴변을 두번째로 사용
            //TODO 긴변을 사용할지 다른변을 사용할지 옵션
            if(minInedx == 0){
                lNeghborInedx = minInedx + 1;
                rNeghborInedx = polygonVertexes.size()-1;
            }
            else if(minInedx == polygonVertexes.size()-1){
                lNeghborInedx = 0;
                rNeghborInedx = minInedx - 1;
            }
            else {
                lNeghborInedx = minInedx + 1;
                rNeghborInedx = minInedx - 1;
            }
            sendRecyclerMessage(String.format("전체%d%n현제%d%n왼쪽%d%n오른쪽%d%n",
                    polygonVertexes.size(),minInedx,lNeghborInedx,rNeghborInedx));

            distanceToRight = firstPoint.distanceTo(polygonVertexes.get(rNeghborInedx));
            distanceToLeft  = firstPoint.distanceTo(polygonVertexes.get(lNeghborInedx));
            if(distanceToLeft > distanceToRight){
                sendRecyclerMessage("왼쪽이 더 길다");
                secondPoint = polygonVertexes.get(lNeghborInedx);
            }
            else {
                sendRecyclerMessage("오른쪽이 더 길다");
                secondPoint = polygonVertexes.get(rNeghborInedx);
            }
            targetLatLngs.add(secondPoint);

            //3.그대로 거리이용해서 나머지 구하기
            //3-1.처음과 두번째포인트의 각도와 입력받은 간격을 이용하여 선 그리기
            double angleFromCoord = getAngleFromCoord(firstPoint,secondPoint);
            sendRecyclerMessage(String.format("각도: %f",angleFromCoord));
            PointF offsetPointF = getXYoffsetfromAngle((float) angleFromCoord, distance);

            //3-2.첫 교점 구하기
            LatLng firstCrossLatLng;
            if(pathBoundDiagonalType == DIAGONAL_TYPE_NE_TO_SW){
                firstCrossLatLng = getIntersection(firstPoint,secondPoint,boundNE,boundSW);
            }
            else{// if(pathBoundDiagonalType == DIAGONAL_TYPE_NW_TO_SE){
                firstCrossLatLng = getIntersection(firstPoint,secondPoint,boundNW,boundSE);
            }

            //3-3.교점과 중심을 비교하여 진행방향 확인하기
            if(firstCrossLatLng.latitude < polygon.getBounds().getCenter().latitude){
                if(firstCrossLatLng.longitude < polygon.getBounds().getCenter().longitude) {
                    pathDirection = DIRECTION_NE;
                }
                else {
                    pathDirection = DIRECTION_NW;
                }
            }
            else{
                if(firstCrossLatLng.longitude < polygon.getBounds().getCenter().longitude) {
                    pathDirection = DIRECTION_SE;
                }
                else {
                    pathDirection = DIRECTION_SW;
                }
            }
            /*
            LatLong offsetLatLng4 = GeoTools.newCoordFromBearingAndDistance(firstCrossLatLng.latitude,firstCrossLatLng.longitude,angleFromCoord,TMP_DISTANCE);
            LatLong offsetLatLng3 = GeoTools.newCoordFromBearingAndDistance(firstCrossLatLng.latitude,firstCrossLatLng.longitude,angleFromCoord,TMP_DISTANCE*2);
            */

            LatLong offsetLatLng4 = GeoTools.moveCoordinate(new LatLong(firstCrossLatLng.latitude,firstCrossLatLng.longitude),offsetPointF.x,offsetPointF.y);
            LatLong offsetLatLng3 = GeoTools.moveCoordinate(new LatLong(firstCrossLatLng.latitude,firstCrossLatLng.longitude),offsetPointF.x*2,offsetPointF.y*2);

            LatLng offsetLatLng1 = new LatLng(offsetLatLng4.getLatitude(),offsetLatLng4.getLongitude());
            LatLng offsetLatLng2 = new LatLng(offsetLatLng3.getLatitude(),offsetLatLng3.getLongitude());


            //4.출력

            //임시 확인용
            tmpMarker1.setMap(null);
            tmpMarker2.setMap(null);
            tmpMarker3.setMap(null);
            tmpMarker4.setMap(null);
            tmpMarker5.setMap(null);
            tmpMarker6.setMap(null);

            tmpMarker1 = new Marker(firstCrossLatLng);
            tmpMarker1.setIcon(MarkerIcons.RED);
            tmpMarker1.setCaptionText("첫교차점");
            tmpMarker1.setMap(naverMap);

            tmpMarker2 = new Marker(offsetLatLng1);
            tmpMarker2.setIcon(MarkerIcons.YELLOW);
            tmpMarker2.setCaptionText("일정거리이동");
            tmpMarker2.setMap(naverMap);

            tmpMarker3 = new Marker(offsetLatLng2);
            tmpMarker3.setIcon(MarkerIcons.PINK);
            tmpMarker3.setCaptionText("일정거리이동2");
            tmpMarker3.setMap(naverMap);

            tmpMarker4 = new Marker(polygonVertexes.get(minInedx));
            tmpMarker4.setIcon(MarkerIcons.GREEN);
            tmpMarker4.setCaptionText("가까운점");
            tmpMarker4.setMap(naverMap);

            tmpMarker5 = new Marker(polygonVertexes.get(lNeghborInedx));
            tmpMarker5.setIcon(MarkerIcons.BLUE);
            tmpMarker5.setCaptionText("왼쪽");
            tmpMarker5.setMap(naverMap);

            tmpMarker6 = new Marker(polygonVertexes.get(rNeghborInedx));
            tmpMarker6.setIcon(MarkerIcons.BLACK);
            tmpMarker6.setCaptionText("오른쪽");
            tmpMarker6.setMap(naverMap);
        }

    }

    public LatLng getIntersection(LatLng l1p1, LatLng l1p2, LatLng l2p1, LatLng l2p2){
        //지구는 둥글고 좌표는 평평하기 때문에 약간의 왜곡이 있을 수 있음
        //아직 왜곡 보정하지 않음
        double a1 = l1p2.longitude - l1p1.longitude;
        double b1 = l1p1.latitude - l1p2.latitude;
        double c1 = a1 * l1p1.latitude + b1 * l1p1.longitude;

        double a2 = l2p2.longitude - l2p1.longitude;
        double b2 = l2p1.latitude - l2p2.latitude;
        double c2 = a2 * l2p1.latitude + b2 * l2p1.longitude;

        double delta = a1 * b2 - a2 * b1;
        return new LatLng((b2 * c1 - b1 * c2) / delta, (a1 * c2 - a2 * c1) / delta);
    }

    public PointF getXYoffsetfromAngle(float angle, float distance){
        angle %= 360;
        if(angle%180 > 90) pathBoundDiagonalType = DIAGONAL_TYPE_NE_TO_SW;
        else pathBoundDiagonalType = DIAGONAL_TYPE_NW_TO_SE;

        float dx = (float) (distance * Math.sin(angle));
        float dy = (float) (distance * Math.cos(angle));

        sendRecyclerMessage(String.format("X거리:%f, Y거리:%f",dx,dy));
        return new PointF(dy,dx);

    }

    public void getDronePolyPath(PolygonOverlay polygon, int distance, float angle){
        int startPoint = START_POINT_NEAREST;
        getDronePolyPath(polygon, distance, angle, startPoint);
    }

    private double getAngleFromCoord(LatLng latLng1, LatLng latLng2) {
        LineLatLong lineLatLong = new LineLatLong(new LatLong(latLng1.latitude,latLng1.longitude),new LatLong(latLng2.latitude,latLng2.longitude));
        return lineLatLong.getHeading();
    }

    public void makePolygon(LatLng latLng){
        Marker marker = new Marker(latLng);
        marker.setHeight(50);
        marker.setWidth(50);
        marker.setMap(naverMap);
        polyMarkers.add(marker);
        polyMarkersLatLng.add(latLng);


        if(polyMarkers.size()>= 3){
            sortMarkerClockwise(polyMarkers);
            sortLatLngClockwise(polyMarkersLatLng);
            polygon.setCoords(polyMarkersLatLng);
            polygon.setMap(naverMap);
        }
        sendRecyclerMessage(String.format("%d",polyMarkers.size()));
    }

    public static void sortLatLngClockwise(List<LatLng> latlngs) {
        float averageX = 0;
        float averageY = 0;

        for (LatLng latLng : latlngs) {
            averageX += latLng.latitude;
            averageY += latLng.longitude;
        }

        final float finalAverageX = averageX / latlngs.size();
        final float finalAverageY = averageY / latlngs.size();

        Comparator<LatLng> comparator = (lhs, rhs) -> {
            double lhsAngle = Math.atan2(lhs.longitude - finalAverageY, lhs.latitude - finalAverageX);
            double rhsAngle = Math.atan2(rhs.longitude - finalAverageY, rhs.latitude - finalAverageX);

            // Depending on the coordinate system, you might need to reverse these two conditions
            if (lhsAngle < rhsAngle) return -1;
            if (lhsAngle > rhsAngle) return 1;

            return 0;
        };

        Collections.sort(latlngs, comparator);
    }

    public static void sortMarkerClockwise(List<Marker> markers) {
        float averageX = 0;
        float averageY = 0;

        for (Marker marker : markers) {
            averageX += marker.getPosition().latitude;
            averageY += marker.getPosition().longitude;
        }

        final float finalAverageX = averageX / markers.size();
        final float finalAverageY = averageY / markers.size();

        Comparator<Marker> comparator = (lhs, rhs) -> {
            double lhsAngle = Math.atan2(lhs.getPosition().latitude - finalAverageY, lhs.getPosition().longitude - finalAverageX);
            double rhsAngle = Math.atan2(rhs.getPosition().latitude - finalAverageY, rhs.getPosition().longitude - finalAverageX);

            // Depending on the coordinate system, you might need to reverse these two conditions
            if (lhsAngle < rhsAngle) return -1;
            if (lhsAngle > rhsAngle) return 1;

            return 0;
        };

        Collections.sort(markers, comparator);
    }

    private void LongClickWarning(@NonNull PointF pointF, @NonNull final LatLng coord) {
        MyAlertDialog builder = new MyAlertDialog(this);
        builder.setTitle("가이드 모드");
        builder.setMessage("클릭한 지점으로 이동하게 됩니다. 이동하시겠습니까?");
        builder.setPositiveButton("예", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // 도착지 마커 생성
                marker_goal.setMap(null);
                marker_goal.setPosition(new LatLng(coord.latitude, coord.longitude));
                marker_goal.setIcon(OverlayImage.fromResource(R.drawable.target));
                marker_goal.setAnchor(new PointF(0.5f,0.5f));
                marker_goal.setWidth(70);
                marker_goal.setHeight(70);
                marker_goal.setMap(naverMap);

                // Guided 모드로 변환
                ChangeToGuidedMode();

                // 지정된 위치로 이동
                GotoTartget();
            }
        });
        builder.setNegativeButton("아니오", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();

            }
        });
        builder.show();

    }

    private void ChangeToGuidedMode() {
        VehicleApi.getApi(this.drone).setVehicleMode(VehicleMode.COPTER_GUIDED, new SimpleCommandListener() {
            @Override
            public void onSuccess() {
                alertUser("가이드 모드로 변경 중...");
            }

            @Override
            public void onError(int executionError) {
                alertUser("가이드 모드 변경 실패 : " + executionError);
            }

            @Override
            public void onTimeout() {
                alertUser("가이드 모드 변경 실패.");
            }
        });
    }

    private void GotoTartget() {
        ControlApi.getApi(this.drone).goTo(
                new LatLong(marker_goal.getPosition().latitude, marker_goal.getPosition().longitude),
                true, new AbstractCommandListener() {
                    @Override
                    public void onSuccess() {
                        alertUser("목적지로 향합니다.");
                    }

                    @Override
                    public void onError(int executionError) {
                        alertUser("이동 할 수 없습니다 : " + executionError);
                    }

                    @Override
                    public void onTimeout() {
                        alertUser("이동 할 수 없습니다.");
                    }
                });
    }

    private void GotoTartget(LatLong latLong) {
        ControlApi.getApi(this.drone).goTo(latLong,true, new AbstractCommandListener() {
                    @Override
                    public void onSuccess() {
                        alertUser("목적지로 향합니다.");
                    }

                    @Override
                    public void onError(int executionError) {
                        alertUser("이동 할 수 없습니다 : " + executionError);
                    }

                    @Override
                    public void onTimeout() {
                        alertUser("이동 할 수 없습니다.");
                    }
                });
    }

    public void btn_hybrid(View view) {
        naverMap.setMapType(NaverMap.MapType.Hybrid);
    }

    public void btn_basic(View view) {
        naverMap.setMapType(NaverMap.MapType.Basic);
    }

    public void initLayout(){
        initAltitudeButton();
        initDroneMarker();
        updateAltBtnVal();
    }

    public void initMap(){

        UiSettings uiSettings = naverMap.getUiSettings();
        uiSettings.setCompassEnabled(false);
        uiSettings.setScaleBarEnabled(false);
        uiSettings.setZoomControlEnabled(false);

        naverMap.setCameraPosition(new CameraPosition(DEFAULT_LATLNG, DEFAULT_ZOOM_LEVEL));
        naverMap.setMapType(MAPTYPE_DEFAULT);

        polygon.setColor(POLYGON_COLOR);
        polygon.setOutlineColor(POLYGON_OUTLINE_COLOR);
        polygon.setOutlineWidth(POLYGON_OUTLINE_WIDTH);

    }

    public void initAltitudeButton(){
        Button altBtnAdd = findViewById(R.id.btn_current_mission_alt_add);
        Button altBtnSub = findViewById(R.id.btn_current_mission_alt_sub);

        altBtnAdd.setVisibility(Button.INVISIBLE);
        altBtnSub.setVisibility(Button.INVISIBLE);
    }

    private void initDroneMarker(){
        droneMarker.setIcon(OverlayImage.fromResource(R.drawable.dronearrow));
        droneMarker.setAnchor(new PointF(0.5f, 0.5f));
        droneMarker.setWidth(100);
        droneMarker.setHeight(330);
        droneMarker.setFlat(true);
    }

    //TODO path값이 없을 경우 처리해야 함
    public void btnPath(View view) {
        if(isMapLinked){
            path.setMap(null);
            isMapLinked = false;
        }
        else{
            path.setMap(naverMap);
            isMapLinked = true;
        }
    }

    public void btnAlt(View view) {
        toggleAltBtnView();
    }

    public void btnAltSub(View view) {
        droneMissionAlt -= 0.5;
        updateAltBtnVal();

    }

    public void btnAltAdd(View view) {
        droneMissionAlt += 0.5;
        updateAltBtnVal();
    }

    void toggleAltBtnView(){
        Button altBtnAdd = (Button)findViewById(R.id.btn_current_mission_alt_add);
        Button altBtnSub = (Button)findViewById(R.id.btn_current_mission_alt_sub);

        if(altBtnAdd.getVisibility()==Button.INVISIBLE){
            altBtnAdd.setEnabled(true);
            altBtnSub.setEnabled(true);
            altBtnAdd.setVisibility(Button.VISIBLE);
            altBtnSub.setVisibility(Button.VISIBLE);
            animButton(altBtnAdd,R.anim.bounce);
            animButton(altBtnSub,R.anim.bounce);
        }
        else{
            animButton(altBtnAdd,R.anim.fadeout);
            animButton(altBtnSub,R.anim.fadeout);
            //TODO 버튼상태가 enabled인지 확인할수있는가? 밑에 invisible관련은 필요가없다 왜냐 fadeout하면 어차피 안보인다
            //그러나 보이지 않을뿐 클릭이 되기 때문에 enable을 false로 바꿔 주었다.
            //altBtnAdd.clearAnimation();
            //altBtnSub.clearAnimation();
            altBtnAdd.setVisibility(Button.INVISIBLE);
            altBtnSub.setVisibility(Button.INVISIBLE);
            altBtnAdd.setEnabled(false);
            altBtnSub.setEnabled(false);
        }
    }

    void updateAltBtnVal(){
        Button altBtn = (Button)findViewById(R.id.btn_current_mission_alt);
        altBtn.setText(String.format("지령 고도 = %.1fm", droneMissionAlt));
    }

    void animButton(Button button, int animationType){
        Button mButton = button;
        //Animation animation = AnimationUtils.loadAnimation(this,R.anim.bounce);
        Animation animation = AnimationUtils.loadAnimation(this,animationType);
        mButton.startAnimation(animation);
    }

    public void hideSystemUI() {
        //hide navigationbar
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                View decorView = getWindow().getDecorView();
                decorView.setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                // Set the content to appear under the system bars so that the
                                // content doesn't resize when the system bars hide and show.
                                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                // Hide the nav bar and status bar
                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_FULLSCREEN);
            }
        }, 100);
    }

    public void toggleCameraLock(View view) {
        ImageButton imageButton = (ImageButton)findViewById(R.id.btn_lock_camera);

        if(isCameraLocked){
            imageButton.setImageResource(R.drawable.unlockedpadlock);
            sendRecyclerMessage("지도 잠금 해제");
            isCameraLocked = false;
        }
        else {
            imageButton.setImageResource(R.drawable.lockedpadlock);
            sendRecyclerMessage("지도 잠금");
            isCameraLocked = true;
        }
    }

    public void btnMissionAB(View view) {
        //todo goto뒤에 latlng좌표 줘야함
        /*
        ControlApi.getApi(this.drone).goTo(, true, new AbstractCommandListener() {
            @Override
            public void onSuccess() {

            }

            @Override
            public void onError(int executionError) {

            }

            @Override
            public void onTimeout() {

            }
        });


        ControlApi.getApi(this.drone).goTo(
                new LatLong(marker_goal.getPosition().latitude, marker_goal.getPosition().longitude),
                true, new AbstractCommandListener() {
                    @Override
                    public void onSuccess() {
                        alertUser("목적지로 향합니다.");
                    }

                    @Override
                    public void onError(int executionError) {
                        alertUser("이동 할 수 없습니다 : " + executionError);
                    }

                    @Override
                    public void onTimeout() {
                        alertUser("이동 할 수 없습니다.");
                    }
                });

         */
    }

    public void btnMissionPoly(View view) {
        if(isPolygonMissionEnabled){
            sendRecyclerMessage("<임시>영역 선택 해제");
            isPolygonMissionEnabled = false;
        }
        else {
            sendRecyclerMessage("<임시>터치하여 다각형 영역을 선택");
            isPolygonMissionEnabled = true;
        }
    }

    public void clear_overlay(View view) {
        polyMarkersLatLng.clear();
        polygon.setMap(null);
        for(Marker marker : polyMarkers){
            marker.setMap(null);
            sendRecyclerMessage(String.format("%d",testCount));
            testCount++;
        }

        polyMarkers.clear();

    }
}

/* 폐기물 리스트
*
*
*   public LatLng moveLatLngtoMeter(LatLng latLngOrigin, PointF pointMeter, int direction){
        double earth = EARTH;
        double pi = Math.PI;
        double meter = (1 / ((2 * pi / 360) * earth)) / 1000;  //1 meter in degree
        double newLatitude;
        double newLongitude;

        if(direction == DIRECTION_NE){
            sendRecyclerMessage("북동");
            newLatitude = latLngOrigin.latitude + (meter*pointMeter.y);
            newLongitude = latLngOrigin.longitude + (meter*pointMeter.x)/Math.cos(latLngOrigin.latitude*(Math.PI/180));
        }else if(direction == DIRECTION_NW){
            sendRecyclerMessage("북서");
            newLatitude = latLngOrigin.latitude + (meter*pointMeter.y);
            newLongitude = latLngOrigin.longitude - (meter*pointMeter.x)/Math.cos(latLngOrigin.latitude*(Math.PI/180));
        }else if(direction == DIRECTION_SE){
            sendRecyclerMessage("남동");
            newLatitude = latLngOrigin.latitude + (meter*pointMeter.y);
            newLongitude = latLngOrigin.longitude + (meter*pointMeter.x)/Math.cos(latLngOrigin.latitude*(Math.PI/180));
        }
        else{//(direction == DIRECTION_SW)
            sendRecyclerMessage("남서");
            newLatitude = latLngOrigin.latitude + (meter*pointMeter.y);
            newLongitude = latLngOrigin.longitude + (meter*pointMeter.x)/Math.cos(latLngOrigin.latitude*(Math.PI/180));
        }

        return new LatLng(newLatitude,newLongitude);
    }

*
*
* */