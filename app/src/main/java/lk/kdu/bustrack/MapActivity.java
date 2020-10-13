package lk.kdu.bustrack;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.directions.route.AbstractRouting;
import com.directions.route.Route;
import com.directions.route.RouteException;
import com.directions.route.Routing;
import com.directions.route.RoutingListener;
import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlaceAutocompleteFragment;
import com.google.android.gms.location.places.ui.PlaceSelectionListener;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.mikepenz.materialdrawer.Drawer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

public class MapActivity extends FragmentActivity implements OnMapReadyCallback, RoutingListener {

    private GoogleMap mMap;
    private FusedLocationProviderClient mFusedLocationClient;
    private SupportMapFragment mapFragment;
    private Button mRequest, mStartHalt, mDestHall, mClear, mCheckBus, mRemoveBus;
    private TextView mStart, mDist, mTime;
    private Drawer result = null;
    private LatLng currentLocation, destLatLng, startHaltLatLng, destHaltLatLng;
    private String selHalt, destination, selDesHalt;
    Location mLastLocation;
    LocationRequest mLocationRequest;
    View mapView;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        polylines = new ArrayList<>();

        NavDrawer nd=new NavDrawer();
        if(result==null){
            result = nd.getDrawer(this,savedInstanceState);
        }
        else {
//            result = nd.getDrawer(this, savedInstanceState);
        }

        mFusedLocationClient=LocationServices.getFusedLocationProviderClient(this);
        mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        mapView=mapFragment.getView();

        mStart=findViewById(R.id.startHalt);
        mDestHall=findViewById(R.id.setDest);
        mRequest=findViewById(R.id.checkStation);
        mStartHalt=findViewById(R.id.setStart);
        mClear=findViewById(R.id.clear);
        mDist=findViewById(R.id.dist);
        mTime=findViewById(R.id.desTime);
        mCheckBus=findViewById(R.id.checkBus);
        mRemoveBus=findViewById(R.id.removeBus);

        mRequest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String userId=FirebaseAuth.getInstance().getCurrentUser().getUid();

                DatabaseReference ref=FirebaseDatabase.getInstance().getReference("cusRequest");
                GeoFire geoFire=new GeoFire(ref);
                geoFire.setLocation(userId, new GeoLocation(mLastLocation.getLatitude(),mLastLocation.getLongitude()), new GeoFire.CompletionListener() {
                    @Override
                    public void onComplete(String s, DatabaseError databaseError) {
                    }
                });

                currentLocation=new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude());
                mMap.addMarker(new MarkerOptions().position(currentLocation).title("You are here").icon(BitmapDescriptorFactory.fromResource(R.mipmap.ic_user_foreground)));

                getClosestHalt();
            }
        });

        mCheckBus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getClosestBus();
                mCheckBus.setVisibility(View.INVISIBLE);
                mRemoveBus.setVisibility(View.VISIBLE);
            }
        });
        mRemoveBus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                removeBuses();
                mRemoveBus.setVisibility(View.INVISIBLE);
                mCheckBus.setVisibility(View.VISIBLE);
            }
        });
        mStartHalt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mStart.setText(selHalt);
            }
        });
        mDestHall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getRouteToMarker();
                mRequest.setVisibility(View.INVISIBLE);
                mClear.setVisibility(View.VISIBLE);
            }
        });
        mClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                erasePolylines();
                mMap.clear();
                mStart.setText("");
                mDist.setText("");
                mTime.setText("");
                mDist.setVisibility(View.INVISIBLE);
                mTime.setVisibility(View.INVISIBLE);
                mClear.setVisibility(View.INVISIBLE);
                mRequest.setVisibility(View.VISIBLE);
            }
        });

        PlaceAutocompleteFragment autocompleteFragment = (PlaceAutocompleteFragment)
                getFragmentManager().findFragmentById(R.id.place_autocomplete_fragment);

        autocompleteFragment.setOnPlaceSelectedListener(new PlaceSelectionListener() {
            @Override
            public void onPlaceSelected(Place place) {
                destination=place.getName().toString();
                destLatLng=place.getLatLng();
                CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLng(destLatLng);
                mMap.animateCamera(cameraUpdate);
                getDestinationHalt();

            }

            @Override
            public void onError(Status status) {

            }
        });

    }

    private void removeBuses() {
        busCount.clear();
        busRoutes.clear();
        busMarkers.clear();
        mMap.clear();
    }

    private HashMap<String, GeoLocation> busCount = new HashMap<String, GeoLocation>();
    private HashMap<String, Marker> busMarkers = new HashMap<String, Marker>();
    private HashMap<String, String> busRoutes = new HashMap<String, String>();
    private void getClosestBus() {
        final DatabaseReference busLocation= FirebaseDatabase.getInstance().getReference().child("activeBuses");
        GeoFire geoFire=new GeoFire(busLocation);
        GeoQuery geoQuery=geoFire.queryAtLocation(new GeoLocation(mLastLocation.getLatitude(), mLastLocation.getLongitude()),4);
        geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(final String key, GeoLocation location) {
                busCount.put(key,location);
                final LatLng sHalt=new LatLng(location.latitude, location.longitude);

                DatabaseReference ref= FirebaseDatabase.getInstance().getReference().child("Buses").child(key);
                ref.addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        if (dataSnapshot.exists() && dataSnapshot.getChildrenCount() > 0) {
                                Map<String, Object> map = (Map<String, Object>) dataSnapshot.getValue();
                                busRoutes.put(key, map.get("routeNo").toString());
                                Marker mMBus=mMap.addMarker(new MarkerOptions().position(sHalt).title(map.get("routeNo").toString()).icon(BitmapDescriptorFactory.fromResource(R.mipmap.ic_bus_foreground)));
                                mMBus.showInfoWindow();
                                busMarkers.put(key, mMBus);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {

                    }
                });
            }

            @Override
            public void onKeyExited(String key) {
                busRoutes.remove(key);
                busCount.remove(key);
                busMarkers.get(key).remove();
                busMarkers.remove(key);
            }

            @Override
            public void onKeyMoved(String key, GeoLocation location) {
                LatLng mBus=new LatLng(location.latitude, location.longitude);
                busMarkers.get(key).setPosition(mBus);
            }

            @Override
            public void onGeoQueryReady() {
                if(busCount.size()<2){
                    getClosestBus();
                }
            }

            @Override
            public void onGeoQueryError(DatabaseError error) {

            }
        });
    }

    private double radius=1.8;
    private int haltFound=0;
    private String[] haltName=new String[5];
    private void getClosestHalt() {
        final DatabaseReference haltLocation=FirebaseDatabase.getInstance().getReference().child("busHalt");
        GeoFire geoFire=new GeoFire(haltLocation);
        final GeoQuery geoQuery=geoFire.queryAtLocation(new GeoLocation(currentLocation.latitude, currentLocation.longitude), radius);
        geoQuery.removeAllListeners();

        geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(String key, GeoLocation location) {
                if(haltFound<5){
                    haltName[haltFound]=key;
                    haltFound++;

                    LatLng sHalt=new LatLng(location.latitude, location.longitude);
                    mMap.addMarker(new MarkerOptions().position(sHalt).title(key).icon(BitmapDescriptorFactory.fromResource(R.mipmap.ic_halt_foreground)));
//                    getHaltLocation(key);
                }
            }

            @Override
            public void onKeyExited(String key) {

            }

            @Override
            public void onKeyMoved(String key, GeoLocation location) {

            }

            @Override
            public void onGeoQueryReady() {
                if(radius<1.5 && haltFound<5){
                    radius+=0.1;
                    getClosestHalt();
                }
            }

            @Override
            public void onGeoQueryError(DatabaseError error) {

            }
        });
        mMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker) {
                selHalt=marker.getTitle();
                startHaltLatLng=marker.getPosition();
                mStartHalt.setVisibility(View.VISIBLE);
                return false;
            }
        });
    }

    private double destRadius=1.0;
    private int destHaltFound=0;
    private String[] destHaltName=new String[5];
    private void getDestinationHalt() {
        final DatabaseReference haltLocation=FirebaseDatabase.getInstance().getReference().child("busHalt");
        GeoFire geoFire=new GeoFire(haltLocation);
        final GeoQuery geoQuery=geoFire.queryAtLocation(new GeoLocation(destLatLng.latitude, destLatLng.longitude), radius);
        geoQuery.removeAllListeners();

        geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(String key, GeoLocation location) {
                if(destHaltFound<5){
                    destHaltName[destHaltFound]=key;
                    destHaltFound++;

                    LatLng dHalt=new LatLng(location.latitude, location.longitude);
                    mMap.addMarker(new MarkerOptions().position(dHalt).title(key).icon(BitmapDescriptorFactory.fromResource(R.mipmap.ic_halt_foreground)));
//                    getHaltLocation(key);
                }
            }

            @Override
            public void onKeyExited(String key) {

            }

            @Override
            public void onKeyMoved(String key, GeoLocation location) {

            }

            @Override
            public void onGeoQueryReady() {
                if(destRadius<1.0 && destHaltFound<5){
                    destRadius+=0.1;
                    getDestinationHalt();
                }
            }

            @Override
            public void onGeoQueryError(DatabaseError error) {

            }
        });
        mMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker) {
                selDesHalt=marker.getTitle();
                destHaltLatLng=marker.getPosition();
                mDestHall.setVisibility(View.VISIBLE);
                return false;
            }
        });
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mLocationRequest = new LocationRequest();
//        mLocationRequest.setInterval(1000);
//        mLocationRequest.setFastestInterval(1000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        if (mapView != null &&
                mapView.findViewById(Integer.parseInt("1")) != null) {
            // Get the button view
            View locationButton = ((View) mapView.findViewById(Integer.parseInt("1")).getParent()).findViewById(Integer.parseInt("2"));
            // and next place it, on bottom right (as Google Maps app)
            RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams)
                    locationButton.getLayoutParams();
            // position on right bottom
            layoutParams.addRule(RelativeLayout.ALIGN_PARENT_TOP, 0);
            layoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
            layoutParams.setMargins(0, 0, 30, 250);
        }

        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.M){
            checkLocationPermission();
        }
    }



    LocationCallback mLocationCallback = new LocationCallback(){
        @Override
        public void onLocationResult(LocationResult locationResult) {
            for(Location location : locationResult.getLocations()){
                if(getApplicationContext()!=null){

//                    if(!customerId.equals("") && mLastLocation!=null && location != null){
//                        rideDistance += mLastLocation.distanceTo(location)/1000;
//                    }
                    mLastLocation = location;


                    LatLng latLng = new LatLng(location.getLatitude(),location.getLongitude());
                    mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
                    mMap.animateCamera(CameraUpdateFactory.zoomTo(15));
                }
            }
        }
    };

    private void checkLocationPermission() {
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED){
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                new AlertDialog.Builder(this)
                        .setTitle("give permission")
                        .setMessage("give permission message")
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                ActivityCompat.requestPermissions(MapActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
                            }
                        })
                        .create()
                        .show();
            }
            else{
                ActivityCompat.requestPermissions(MapActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch(requestCode){
            case 1:{
                if(grantResults.length >0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED){
                        mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.myLooper());
                        mMap.setMyLocationEnabled(true);
                    }
                } else{
                    Toast.makeText(getApplicationContext(), "Please provide the permission", Toast.LENGTH_LONG).show();
                }
                break;
            }
        }
    }

    private void getRouteToMarker() {
        Routing routing = new Routing.Builder()
                .travelMode(AbstractRouting.TravelMode.DRIVING)
                .withListener(this)
                .alternativeRoutes(false)
                .waypoints(startHaltLatLng, destHaltLatLng)
                .key("AIzaSyCy17AM0LyvcxWFgzgCYaeUgm36nu9PZWc")
                .build();
        routing.execute();

    }

    private List<Polyline> polylines;
    private static final int[] COLORS = new int[]{R.color.primary_dark_material_light};
    @Override
    public void onRoutingFailure(RouteException e) {
        if(e != null) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }else {
            Toast.makeText(this, "Something went wrong, Try again", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRoutingStart() {

    }

    @Override
    public void onRoutingSuccess(ArrayList<Route> route, int shortestRouteIndex) {
        if(polylines.size()>0) {
            for (Polyline poly : polylines) {
                poly.remove();
            }
        }

        polylines = new ArrayList<>();
        //add route(s) to the map.
        for (int i = 0; i <route.size(); i++) {

            //In case of more than 5 alternative routes
            int colorIndex = i % COLORS.length;

            PolylineOptions polyOptions = new PolylineOptions();
            polyOptions.color(getResources().getColor(COLORS[colorIndex]));
            polyOptions.width(10 + i * 5);
            polyOptions.addAll(route.get(i).getPoints());
            Polyline polyline = mMap.addPolyline(polyOptions);
            polylines.add(polyline);

            mDist.setVisibility(View.VISIBLE);
            mTime.setVisibility(View.VISIBLE);
            String dist= String.valueOf((route.get(i).getDistanceValue())/1000)+" K.m";
            String time= String.valueOf((route.get(i).getDurationValue())/60)+" min";
            mDist.setText(dist);
            mTime.setText(time);
//            Toast.makeText(getApplicationContext(),"Route "+ (i+1) +": distance - "+ route.get(i).getDistanceValue()+": duration - "+ route.get(i).getDurationValue(),Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRoutingCancelled() {

    }
    private void erasePolylines(){
        for(Polyline line : polylines){
            line.remove();
        }
        polylines.clear();
    }
}
