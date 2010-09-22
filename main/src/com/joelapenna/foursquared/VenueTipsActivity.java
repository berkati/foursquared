/**
 * Copyright 2009 Joe LaPenna
 */

package com.joelapenna.foursquared;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;

import com.joelapenna.foursquare.types.Group;
import com.joelapenna.foursquare.types.Tip;
import com.joelapenna.foursquare.types.Todo;
import com.joelapenna.foursquare.types.Venue;
import com.joelapenna.foursquared.app.LoadableListActivity;
import com.joelapenna.foursquared.util.VenueUtils;
import com.joelapenna.foursquared.widget.SeparatedListAdapter;
import com.joelapenna.foursquared.widget.TipsListAdapter;

/**
 * @author Joe LaPenna (joe@joelapenna.com)
 * @author Mark Wyszomierski (markww@gmail.com)
 *   -modified to start TipActivity on tip click (2010-03-25)
 *   -added photos for tips (2010-03-25)
 *   -refactored for new VenueActivity design (2010-09-16)
 */
public class VenueTipsActivity extends LoadableListActivity {
    
	public static final String TAG = "VenueTipsActivity";
    public static final boolean DEBUG = FoursquaredSettings.DEBUG;

    public static final String INTENT_EXTRA_VENUE = Foursquared.PACKAGE_NAME
            + ".VenueTipsActivity.INTENT_EXTRA_VENUE";
    public static final String INTENT_EXTRA_RETURN_VENUE = Foursquared.PACKAGE_NAME 
	        + ".VenueTipsActivity.INTENT_EXTRA_RETURN_VENUE";
    

    private static final int ACTIVITY_TIP = 500;
    
    private SeparatedListAdapter mListAdapter;
    private StateHolder mStateHolder;

        
    private BroadcastReceiver mLoggedOutReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) Log.d(TAG, "onReceive: " + intent);
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        registerReceiver(mLoggedOutReceiver, new IntentFilter(Foursquared.INTENT_ACTION_LOGGED_OUT));

        Object retained = getLastNonConfigurationInstance();
        if (retained != null && retained instanceof StateHolder) {
            mStateHolder = (StateHolder) retained;
        } else {
            mStateHolder = new StateHolder();
            if (getIntent().hasExtra(INTENT_EXTRA_VENUE)) {
            	mStateHolder.setVenue((Venue)getIntent().getExtras().getParcelable(INTENT_EXTRA_VENUE));
            } else {
                Log.e(TAG, "VenueTipsActivity requires a venue parcel its intent extras.");
                finish();
                return;
            }
        }
        
        ensureUi();
    }
    
    @Override
    public void onPause() {
        super.onPause();
        
        if (isFinishing()) {
            mListAdapter.removeObserver();
            unregisterReceiver(mLoggedOutReceiver);
        }
    }
    
    @Override
    public Object onRetainNonConfigurationInstance() {
        return mStateHolder;
    }

    private void ensureUi() {
    	
    	Group<Tip> tips = mStateHolder.getVenue().getTips();

    	TipsListAdapter groupAdapter = new TipsListAdapter(this,
                ((Foursquared) getApplication()).getRemoteResourceManager());
    	groupAdapter.setDisplayTipVenueTitles(false);
        groupAdapter.setGroup(tips);
        
        String title = getResources().getString(R.string.venue_tips_activity_title, tips.size());
        
    	mListAdapter = new SeparatedListAdapter(this);
        mListAdapter.addSection(title, groupAdapter);
        
        ListView listView = getListView();
        listView.setAdapter(mListAdapter);
        listView.setSmoothScrollbarEnabled(true);
        listView.setDividerHeight(0);
        listView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            	// The tip that was clicked won't have its venue member set, since we got
            	// here by viewing the parent venue. In this case, we request that the tip
            	// activity not let the user recursively start drilling down past here.
            	// Create a dummy venue which has only the name and address filled in.
            	Venue venue = new Venue();
            	venue.setName(mStateHolder.getVenue().getName());
            	venue.setAddress(mStateHolder.getVenue().getAddress());
            	venue.setCrossstreet(mStateHolder.getVenue().getCrossstreet());
            	
            	Tip tip = (Tip)parent.getAdapter().getItem(position);
            	tip.setVenue(venue);
            	
                Intent intent = new Intent(VenueTipsActivity.this, TipActivity.class);
                intent.putExtra(TipActivity.EXTRA_TIP_PARCEL, tip);
                intent.putExtra(TipActivity.EXTRA_VENUE_CLICKABLE, false);
                startActivityForResult(intent, ACTIVITY_TIP);
            }
        });
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    	if (requestCode == ACTIVITY_TIP && resultCode == Activity.RESULT_OK) {
    		if (data.hasExtra(TipActivity.EXTRA_TIP_RETURNED)) {
	    		Tip tip = (Tip)data.getParcelableExtra(TipActivity.EXTRA_TIP_RETURNED);
	    		Todo todo = data.hasExtra(TipActivity.EXTRA_TODO_RETURNED) ? 
	    				(Todo)data.getParcelableExtra(TipActivity.EXTRA_TODO_RETURNED) : null;
	    		updateTip(tip, todo);
    		}
        }
    }
    
    private void updateTip(Tip tip, Todo todo) {
    	// Changes to a tip status can produce or remove a to-do from
    	// the venue, update it now.
    	VenueUtils.handleTipChange(mStateHolder.getVenue(), tip, todo);
    	
    	mListAdapter.notifyDataSetInvalidated();
    	prepareResultIntent();
    }
    
    private void prepareResultIntent() {
    	Intent intent = new Intent();
    	intent.putExtra(INTENT_EXTRA_RETURN_VENUE, mStateHolder.getVenue());
    	setResult(Activity.RESULT_OK, intent);
    }
    
    
    private static class StateHolder {
        
        private Venue mVenue;
        
        public StateHolder() {
        }
 
        public Venue getVenue() {
            return mVenue;
        }
        
        public void setVenue(Venue venue) {
        	mVenue = venue;
        }
        /*
        public void updateTip(Tip tip) {
            for (Tip it : mVenue.getTips()) {
                if (it.getId().equals(tip.getId())) {
                    it.setStatus(tip.getStatus());
                    break;
                }
            }
        }
        
        public void addTodo(Tip tip, Todo todo) {
        	Log.e(TAG, "addTodo: " + tip.getId() + ":" + todo.getId());
        	
        	mVenue.setHasTodo(true);
        	
        	// If found a todo linked to the tip ID, then overwrite to-do attributes
        	// with newer todo object.
        	for (Todo it : mVenue.getTodos()) {
        		if (it.getTip().getId().equals(tip.getId())) {
                	Log.e(TAG, "   add todo, already found!: " + tip.getId() + ":" + it.getId());
        			it.setId(todo.getId());
        			it.setCreated(todo.getCreated());
        			return;
        		}
        	}
        	
        	Log.e(TAG, "  add todo, not found, adding: " + tip.getId() + ":" + todo.getId());
        	
        	mVenue.getTodos().add(todo);
        }
        
        public void removeTodo(Tip tip) {
        	Log.e(TAG, "removeTodo: " + tip.getId() + ":(to-do id never expected for removing to-do).");
        	
        	for (Todo it : mVenue.getTodos()) {
        		if (it.getTip().getId().equals(tip.getId())) {
                	Log.e(TAG, "  remove todo, found, removing!: " + tip.getId());
        			mVenue.getTodos().remove(it);
        			break;
        		}
        	}

        	//Log.e(TAG, "  remove todo, not found at all, nothign to do: " + tip.getId() + ":" + todoId);
        	
        	if (mVenue.getTodos().size() > 0) {
        		mVenue.setHasTodo(true);
        	} else {
        		mVenue.setHasTodo(false);
        	}
        }
        */
    }
}
