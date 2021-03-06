package org.taitasciore.android.hospitalk.hospital;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.toptoche.searchablespinnerlibrary.SearchableSpinner;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.taitasciore.android.event.RequestCountryEvent;
import org.taitasciore.android.event.RequestDeterminedLocationEvent;
import org.taitasciore.android.event.RequestLocationEvent;
import org.taitasciore.android.event.SendCountryEvent;
import org.taitasciore.android.event.SendDeterminedLocationEvent;
import org.taitasciore.android.event.SendLocationEvent;
import org.taitasciore.android.hospitalk.ActivityUtils;
import org.taitasciore.android.hospitalk.R;
import org.taitasciore.android.model.Activity;
import org.taitasciore.android.model.City;
import org.taitasciore.android.model.Hospital;
import org.taitasciore.android.model.LocationResponse;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

/**
 * Created by roberto on 24/04/17.
 */

public class SearchHospitalsFragment extends Fragment implements SearchHospitalsContract.View {

    private double lat;
    private double lon;
    private int mOffset;
    private String mCountryId = "";
    private int mCountryPos;
    private String mCityId = "";
    private int mCityPos;
    private String mActivityId = "";
    private int mActivityPos;
    private String mReviewOrder = "";
    private int mReviewOrderPos;
    private String mReviewRank = "";
    private int mReviewRankPos;
    private String mQuery = "";
    private boolean mChangedCountry;
    private boolean mDeterminedLocation;
    private ArrayList<LocationResponse.Country> mCountries;
    private ArrayList<City> mCities;
    private ArrayList<Activity> mActivities;
    private ArrayList<Hospital> mHospitals;

    private SearchHospitalsContract.Presenter mPresenter;

    private ArrayAdapter<String> mAdapterRatings;
    private ArrayAdapter<String> mAdapterOptions;
    private ArrayAdapter<String> mAdapterCountries;
    private ArrayAdapter<String> mAdapterCities;
    private ArrayAdapter<String> mAdapterActivities;

    private ProgressDialog mDialog;

    @BindView(R.id.list) RecyclerView mRecyclerView;
    private RecyclerView.LayoutManager mLayoutMngr;
    private SearchHospitalAdapter mAdapter;

    @BindView(R.id.tvSearch) AutoCompleteTextView mTvSearch;
    @BindView(R.id.btnSearch) Button mBtnSearch;

    @BindView(R.id.tvResetFilters) TextView mTvResetFilters;

    @BindView(R.id.fab) FloatingActionButton mFab;

    @BindView(R.id.cbCloseHospitals) CheckBox mCbCloseHospitals;

    @BindView(R.id.spCountry) SearchableSpinner mSpCountry;
    @BindView(R.id.spCity) SearchableSpinner mSpCity;
    @BindView(R.id.spOrder) Spinner mSpOrder;
    @BindView(R.id.spActivity) Spinner mSpActivity;
    @BindView(R.id.spRank) Spinner mSpRank;

    public static SearchHospitalsFragment newInstance(String query) {
        SearchHospitalsFragment f = new SearchHospitalsFragment();
        Bundle args = new Bundle();
        args.putString("query", query);
        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_search_hospitals, container, false);
        ButterKnife.bind(this, v);
        initRecyclerView();
        initAdapters();

        mSpCountry.setTitle("Seleccione un país");
        mSpCountry.setPositiveButton("cerrar");
        mSpCity.setTitle("Seleccione una ciudad");
        mSpCity.setPositiveButton("cerrar");

        return v;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (mPresenter == null) mPresenter = new SearchHospitalsPresenter(this);
        else mPresenter.bindView(this);
    }

    @Override
    public void onDetach() {
        hideProgress();
        super.onDetach();
        if (mPresenter != null) mPresenter.unbindView();
    }

    @Override
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
    }

    @Override
    public void onResume() {
        super.onResume();

        mSpOrder.setSelection(mReviewOrderPos, false);
        mSpRank.setSelection(mReviewRankPos, false);

        if (mCountries == null) {
            mPresenter.getCountries();
        } else {
            showCountries(mCountries);
            mSpCountry.setSelection(mCountryPos, false);
        }
        if (mCities != null) {
            showCities(mCities);
            mSpCity.setSelection(mCityPos, false);
        }
        if (mActivities != null) {
            showActivities(mActivities);
            mSpActivity.setSelection(mActivityPos, false);
        }

        if (mHospitals != null) addHospitals(mHospitals);

        if (getArguments() != null && getArguments().containsKey("query")) {
            mQuery = getArguments().getString("query");
            mTvSearch.setText(mQuery);
        }

        initListeners();
    }

    @Override
    public void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
        clearListeners();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void determinedLocation(SendDeterminedLocationEvent event) {
        mDeterminedLocation = event.determined;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void getLocation(SendLocationEvent event) {
        lat = event.lat;
        lon = event.lon;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void getCountry(SendCountryEvent event) {
        mCountryId = event.country;
    }

    @Override
    public void bindPresenter(SearchHospitalsContract.Presenter presenter) {
        mPresenter = presenter;
        mPresenter.bindView(this);
    }

    @Override
    public void showProgress() {
        mDialog = ActivityUtils.showProgressDialog(getActivity());
    }

    @Override
    public void hideProgress() {
        if (mDialog != null && mDialog.isShowing()) mDialog.dismiss();
    }

    @Override
    public void showFab() {
        mFab.setVisibility(View.VISIBLE);
    }

    @Override
    public void showNoResultsMessage() {
        Toast.makeText(getActivity(), "No hay hospitales que mostrar", Toast.LENGTH_SHORT).show();
    }

    @OnClick(R.id.fab) void onFabClicked() {
        search(false);
    }

    @Override
    public void initRecyclerView() {
        mLayoutMngr = new LinearLayoutManager(getActivity());
        mRecyclerView.setLayoutManager(mLayoutMngr);
        mRecyclerView.setNestedScrollingEnabled(false);
    }

    @Override
    public void initAdapters() {
        ArrayList<String> list = new ArrayList<>();
        list.add("País");
        mAdapterCountries = new ArrayAdapter<>(
                getActivity(), android.R.layout.simple_spinner_item, list);
        mAdapterCountries.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSpCountry.setAdapter(mAdapterCountries);

        list = new ArrayList<>();
        list.add("Ciudad");
        mAdapterCities = new ArrayAdapter<>(
                getActivity(), android.R.layout.simple_spinner_item, list);
        mAdapterCities.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSpCity.setAdapter(mAdapterCities);

        list = new ArrayList<>();
        list.add("Actividad");
        mAdapterActivities = new ArrayAdapter(
                getActivity(), android.R.layout.simple_spinner_item, list);
        mAdapterActivities.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSpActivity.setAdapter(mAdapterActivities);

        String[] ratings = {
                getString(R.string.n_opiniones),
                getString(R.string.ascendente),
                getString(R.string.descendente)
        };

        mAdapterRatings = new ArrayAdapter<>(
                getActivity(), android.R.layout.simple_spinner_item, ratings);
        mAdapterRatings.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSpOrder.setAdapter(mAdapterRatings);

        String[] option = {
                getString(R.string.valoracion),
                getString(R.string.peor_valorado),
                getString(R.string.mejor_valorado)
        };

        mAdapterOptions = new ArrayAdapter<>(
                getActivity(), android.R.layout.simple_spinner_item, option);
        mAdapterOptions.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSpRank.setAdapter(mAdapterOptions);

        mAdapter = new SearchHospitalAdapter(getActivity());
        mRecyclerView.setAdapter(mAdapter);
    }

    @Override
    public void initListeners() {
        mCbCloseHospitals.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                if (checked) {
                    EventBus.getDefault().post(new RequestDeterminedLocationEvent());
                    EventBus.getDefault().post(new RequestLocationEvent());
                    EventBus.getDefault().post(new RequestCountryEvent());

                    mAdapter = new SearchHospitalAdapter(getActivity());
                    mRecyclerView.setAdapter(mAdapter);

                    if (mDeterminedLocation && !mCountryId.equals("") && mCountries != null) {
                        Log.i("country id", mCountryId);
                        int pos = getCountryPosition(mCountryId);

                        if (pos > -1) {
                            blockFilter();
                            mSpCountry.setSelection(pos + 1, false);
                        }
                    }
                } else {
                    unblockFilter();
                }
            }
        });

        mSpCountry.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long l) {
                mOffset = 0;
                mCountryPos = pos;

                if (pos > 0) {
                    mChangedCountry = true;
                    mCountryId = mCountries.get(pos - 1).getCountryid();
                    Log.i("country id", mCountryId);
                    search(true);
                } else {
                    mChangedCountry = true;
                    mCountryId = "";
                }

                mCityId = "";
                mCityPos = 0;
                mSpCity.setSelection(0, false);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        mSpCity.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long l) {
                mOffset = 0;
                mCityPos = pos;

                if (pos > 0) {
                    if (mCountryId != null && !mCountryId.isEmpty()) {
                        mCityId = mCities.get(pos - 1).getIdCity();
                        Log.i("city id", mCityId + "");
                        if (!mChangedCountry) search(false);
                    } else {
                        showCountryNotSelectedError();
                    }
                } else {
                    mCityId = "";
                    if (mCountryId != null && !mCountryId.isEmpty() && !mChangedCountry) {
                        search(false);
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        mSpActivity.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long l) {
                mOffset = 0;
                mActivityPos = pos;

                if (pos > 0) {
                    if (mCountryId != null && !mCountryId.isEmpty()) {
                        mActivityId = mActivities.get(pos - 1).getIdActivity();
                        Log.i("activity id", mActivityId + "");
                        search(false);
                    } else {
                        showCountryNotSelectedError();
                    }
                } else {
                    mActivityId = "";
                    if (mCountryId != null && !mCountryId.isEmpty()) search(false);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        mSpOrder.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long l) {
                mOffset = 0;
                mReviewOrderPos = pos;

                if (pos > 0) {
                    if (mCountryId != null && !mCountryId.isEmpty()) {
                        if (pos == 1) mReviewOrder = "ASC";
                        else mReviewOrder = "DESC";
                        search(false);
                    } else {
                        showCountryNotSelectedError();
                    }
                } else {
                    mReviewOrder = "";
                    if (mCountryId != null && !mCountryId.isEmpty()) search(false);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        mSpRank.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long l) {
                mOffset = 0;
                mReviewRankPos = pos;

                if (pos > 0) {
                    mReviewRank = pos+"";

                    if (mCountryId != null) {
                        search(false);
                    } else {
                        showCountryNotSelectedError();
                    }
                } else {
                    mReviewRank = "";
                    if (mCountryId != null && !mCountryId.isEmpty()) search(false);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        mTvResetFilters.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mCountryId = "";
                mCountryPos = 0;
                mCityId = "";
                mCityPos = 0;
                mReviewRank = "";
                mReviewRankPos = 0;
                mReviewOrder = "";
                mReviewOrderPos = 0;
                mActivityId = "";
                mActivityPos = 0;
                mQuery = "";

                mSpCountry.setSelection(0, false);
                mSpCity.setSelection(0, false);
                mSpOrder.setSelection(0, false);
                mSpActivity.setSelection(0, false);
                mSpRank.setSelection(0, false);
                mTvSearch.setText("");
                mAdapter = new SearchHospitalAdapter(getActivity());
                mRecyclerView.setAdapter(mAdapter);
            }
        });
    }

    @Override
    public void clearListeners() {
        mCbCloseHospitals.setOnCheckedChangeListener(null);
        mSpCountry.setOnItemSelectedListener(null);
        mSpCity.setOnItemSelectedListener(null);
        mSpRank.setOnItemSelectedListener(null);
        mSpActivity.setOnItemSelectedListener(null);
        mSpOrder.setOnItemSelectedListener(null);
        mTvResetFilters.setOnClickListener(null);
    }

    @Override
    public void incrementOffset() {
        mOffset += SearchHospitalsPresenter.LIMIT;
    }

    @Override
    public void showCountries(ArrayList<LocationResponse.Country> countries) {
        mCountries = countries;
        mAdapterCountries.clear();
        mAdapterCountries.add("País");
        for (LocationResponse.Country c : countries) {
            if (c != null && c.getCountryname() != null && !c.getCountryname().isEmpty()) {
                mAdapterCountries.add(c.getCountryname());
            }
        }
        if (mCountryPos == 0 && mCountries != null) {
            EventBus.getDefault().post(new RequestCountryEvent());
            int pos = getCountryPosition(mCountryId);

            if (pos > -1) {
                mCountryPos = pos + 1;
                mSpCountry.setSelection(mCountryPos, false);
            }
        }
    }

    @Override
    public void showCities(ArrayList<City> cities) {
        mCities = cities;
        mAdapterCities.clear();
        mAdapterCities.add("Ciudad");
        for (City c : cities) {
            if (c != null && c.getCityName() != null && !c.getCityName().isEmpty()) {
                mAdapterCities.add(c.getCityName());
            }
        }

        if (mChangedCountry) {

        }
    }

    @Override
    public void showActivities(ArrayList<Activity> activities) {
        mActivities = activities;
        mAdapterActivities.clear();
        mAdapterActivities.add("Actividad");
        for (Activity a : activities) {
            if (a != null && a.getActivityName() != null && !a.getActivityName().isEmpty()) {
                mAdapterActivities.add(a.getActivityName());
            }
        }

        if (mChangedCountry) {

        }
    }

    @Override
    public void showHospitals(ArrayList<Hospital> hospitals) {
        mChangedCountry = false;
        mHospitals = hospitals;
        mAdapter = new SearchHospitalAdapter(getActivity(), hospitals);
        mRecyclerView.setAdapter(mAdapter);
    }

    @Override
    public void addHospitals(ArrayList<Hospital> hospitals) {
        mChangedCountry = false;
        try {
            for (Hospital h : hospitals) {
                if (h != null) mAdapter.add(h);
            }
            mHospitals = mAdapter.getList();
        } catch (ConcurrentModificationException e){}
    }

    @Override
    public void blockFilter() {
        mSpCountry.setEnabled(false);
    }

    @Override
    public void unblockFilter() {
        mSpCountry.setEnabled(true);
    }

    @Override
    public void showCountryNotSelectedError() {
        Toast.makeText(getActivity(), getString(R.string.country_not_selected_error), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void showNetworkError() {
        Toast.makeText(getActivity(), getString(R.string.network_error), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void showNetworkFailedError() {
        Toast.makeText(getActivity(), getString(R.string.network_failed_error), Toast.LENGTH_SHORT).show();
    }

    @OnClick(R.id.btnSearch) void onSearchClicked() {
        String query = mTvSearch.getText().toString();
        if (!query.isEmpty()) {
            if (mCountryId != null && !mCountryId.isEmpty()) {
                mQuery = query;
                search(mCities == null);
            } else {
                showCountryNotSelectedError();
            }
        }
    }

    private void search(boolean cities) {
        if (mCountryId != null && !mCountryId.isEmpty()) {
            if (cities) {
                mPresenter.searchHospitalsAndCities(
                        mCountryId, mCityId, mActivityId, mReviewOrder, mReviewRank, mOffset, mQuery);
            } else {
                mPresenter.searchHospitals(
                        mCountryId, mCityId, mActivityId, mReviewOrder, mReviewRank, mOffset, mQuery);
            }
        } else {
            showCountryNotSelectedError();
        }
    }

    private int getCountryPosition(String countryId) {
        int i = 0;

        for (LocationResponse.Country c : mCountries) {
            if (c.getCountryid().equalsIgnoreCase(countryId)) return i;
            i++;
        }

        return -1;
    }
}
