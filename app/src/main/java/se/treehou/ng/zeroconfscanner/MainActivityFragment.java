package se.treehou.ng.zeroconfscanner;

import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;

import javax.jmdns.ServiceEvent;

import se.treehou.ng.zeroconfscanner.scanner.Scanner;

/**
 * A placeholder fragment containing a simple view.
 */
public class MainActivityFragment extends Fragment {

    private static final String TAG = MainActivityFragment.class.getSimpleName();

    private Scanner scanner;
    private ServiceAdapter adapter;

    public MainActivityFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_main, container, false);

        RecyclerView lstServices = (RecyclerView) rootView.findViewById(R.id.lst_services);
        lstServices.setLayoutManager(new LinearLayoutManager(getContext()));
        lstServices.setItemAnimator(new DefaultItemAnimator());

        adapter = new ServiceAdapter();
        lstServices.setAdapter(adapter);

        return rootView;
    }

    @Override
    public void onResume() {
        super.onResume();

        scanner = new Scanner(getContext());
        scanner.setServerDiscoveryListener(new Scanner.DiscoveryListener() {
            @Override
            public void onUpdate(final List<ServiceEvent> services) {
                Log.d(TAG, "Adding " + services.size() + " services");

                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        adapter.setServices(services);
                    }
                });
            }
        });
        scanner.startScan();
    }

    @Override
    public void onPause() {
        super.onPause();

        scanner.stopScan();
    }

    class ServiceAdapter extends RecyclerView.Adapter<ServiceAdapter.ServiceHolder>{

        private static final int TYPE_COMPACT = 1;
        private static final int TYPE_EXPANDED = 2;

        class ServiceHolder extends RecyclerView.ViewHolder {

            private TextView lblServiceName;
            private TextView lblServiceType;
            private TextView lblServiceServer;
            private TextView lblServiceApplication;
            private LinearLayout louProperty;

            public ServiceHolder(View itemView) {
                super(itemView);
                lblServiceName = (TextView) itemView.findViewById(R.id.lbl_service_name);
                lblServiceType = (TextView) itemView.findViewById(R.id.lbl_service_type);
                lblServiceApplication = (TextView) itemView.findViewById(R.id.lbl_service_application);
                lblServiceServer = (TextView) itemView.findViewById(R.id.lbl_service_server);
                louProperty = (LinearLayout) itemView.findViewById(R.id.lou_property);
            }

            public void setEvent(final ServiceEvent serviceEvent){
                lblServiceName.setText(serviceEvent.getName());

                String serviceType = serviceEvent.getType();
                String[] typePath = serviceType.split("\\.");
                if(typePath.length > 0){
                    serviceType = typePath[0].replace("_"," ").trim();
                }
                lblServiceType.setText(serviceType);

                if(lblServiceServer != null) lblServiceServer.setText(serviceEvent.getInfo().getURLs()[0]);
                if(lblServiceApplication != null) lblServiceApplication.setText(serviceEvent.getInfo().getServer() + ":" + serviceEvent.getInfo().getPort());

                if(louProperty != null){
                    louProperty.removeAllViews();
                    Enumeration<String> propertyIterator = serviceEvent.getInfo().getPropertyNames();
                    while(propertyIterator.hasMoreElements()){
                        String key = propertyIterator.nextElement();
                        String value = serviceEvent.getInfo().getPropertyString(key);

                        LayoutInflater inflater = LayoutInflater.from(getContext());
                        View propertyItem = inflater.inflate(R.layout.item_property, louProperty, false);
                        TextView lblProperty = (TextView) propertyItem.findViewById(R.id.lbl_property);
                        lblProperty.setText(Html.fromHtml(getString(R.string.property, key, value)));

                        louProperty.addView(propertyItem);
                    }
                }

                itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if(selectedService == serviceEvent) {
                            selectedService = null;
                        } else {
                            selectedService = serviceEvent;
                        }
                        notifyDataSetChanged();
                    }
                });
            }
        }

        private ServiceEvent selectedService = null;
        private List<ServiceEvent> services = new ArrayList<>();

        @Override
        public ServiceHolder onCreateViewHolder(ViewGroup parent, int viewType) {

            ServiceHolder holder;
            if(viewType == TYPE_EXPANDED) {
                LayoutInflater inflater = LayoutInflater.from(getContext());
                View itemView = inflater.inflate(R.layout.item_service_expanded, parent, false);
                holder = new ServiceHolder(itemView);
            }
            else {
                LayoutInflater inflater = LayoutInflater.from(getContext());
                View itemView = inflater.inflate(R.layout.item_service, parent, false);
                holder = new ServiceHolder(itemView);
            }

            return holder;
        }

        @Override
        public void onBindViewHolder(ServiceHolder holder, int position) {
            ServiceEvent serviceEvent = services.get(position);
            holder.setEvent(serviceEvent);
        }

        @Override
        public int getItemCount() {
            return services.size();
        }

        @Override
        public int getItemViewType(int position) {

            ServiceEvent event = services.get(position);
            return event == selectedService ? TYPE_EXPANDED : TYPE_COMPACT;
        }

        public void setServices(List<ServiceEvent> services){

            Collections.sort(services, new Comparator<ServiceEvent>() {
                @Override
                public int compare(ServiceEvent lhs, ServiceEvent rhs) {
                    return lhs.getType().compareTo(rhs.getType());
                }
            });

            this.services = services;

            notifyDataSetChanged();
        }
    }
}
