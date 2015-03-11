package dev.jinkim.snappollandroid.ui.newpoll;


import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SearchView;

import com.afollestad.materialdialogs.MaterialDialog;
import com.gc.materialdesign.views.ButtonFloat;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.plus.People;
import com.google.android.gms.plus.Plus;
import com.google.android.gms.plus.model.people.PersonBuffer;

import java.util.ArrayList;
import java.util.List;

import dev.jinkim.snappollandroid.R;
import dev.jinkim.snappollandroid.model.RowFriend;

/**
 * Created by Jin on 3/6/15.
 */
public class NewPollFriendsFragment extends Fragment {

    public static String TAG = "NewPollFriendsFragment";

    private NewPollActivity mActivity;
    private NewPollController controller;

    private ListView listView;
    private SelectedFriendListAdapter adapter;
    private List<RowFriend> retrievedFriends;

    private ButtonFloat fabSelectFriends;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.frag_new_poll_friends, container, false);
        setHasOptionsMenu(true);
        mActivity = (NewPollActivity) getActivity();
        controller = mActivity.getController();

        mActivity.getSupportActionBar().setTitle("Invite Friends");

        return rootView;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        initViews(view);

        List<RowFriend> selectedFriends = controller.getFriends();
        if (selectedFriends != null && selectedFriends.size() > 0) {
            updateSelectedFriends(selectedFriends);
        } else {
            retrieveFriendsFromGPlus();
        }
    }

    private void showChooseFriendsDialog(List<RowFriend> friends) {
        // set up custom view for dialog - color indicator, edit text
        LayoutInflater vi = (LayoutInflater) mActivity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final View content = vi.inflate(R.layout.dialog_content_choose_friends, null);


        final ChooseFriendListAdapter adapter = new ChooseFriendListAdapter(mActivity, friends);

        // sparse boolean array to keep up with selected friend list
        final SparseBooleanArray selected = new SparseBooleanArray(friends.size());

        for (int i = 0; i < friends.size(); i++) {
            // if preselected from existing list, then update the sparse array
            if (friends.get(i).selected) {
                selected.append(i, true);
            }
        }

        /* SET UP SELECT FRIENDS LIST VIEW */
        final ListView listView = (ListView) content.findViewById(R.id.friends_dialog_lv_friends);
        listView.setTextFilterEnabled(true);
        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        listView.setItemsCanFocus(false);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (adapter.getItem(position).selected) {
                    adapter.getItem(position).selected = false;
                    adapter.getItemFromOriginalList(adapter.getOriginalIndex(position)).selected = false;
                    selected.delete(position);
                } else {
                    adapter.getItem(position).selected = true;
                    adapter.getItemFromOriginalList(adapter.getOriginalIndex(position)).selected = true;
                    selected.append(position, true);
                }
                Log.d(TAG, "Selected friend position: " + String.valueOf(position));
                Log.d(TAG, "Selected friend id: " + String.valueOf(id));
                adapter.notifyDataSetChanged();
            }
        });
        listView.setAdapter(adapter);

        SearchView svSearch = (SearchView) content.findViewById(R.id.friends_dialog_sv_search);
        svSearch.setQueryHint("Search friend from Google+");
        svSearch.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                adapter.getFilter().filter(s);
                return true;
            }
        });

        /* SET UP SELECT FRIENDS DIALOG */
        String dialogTitle = "G+ Choose friends";

        boolean wrapInScrollView = false;
        MaterialDialog dialog = new MaterialDialog.Builder(mActivity)
                .title(dialogTitle)
                .customView(content, wrapInScrollView)
                .positiveText("Save")
//                .positiveText(R.string.agree)
                .negativeText("Cancel")
//                .negativeText(R.string.disagree)
                .callback(new MaterialDialog.ButtonCallback() {

                    @Override
                    public void onPositive(MaterialDialog dialog) {
                        /* update selected friends */
                        Log.d(TAG, selected.toString());

                        // TODO: grab the keys (positions) that are true in value
                        List<RowFriend> selectedFriends = new ArrayList<RowFriend>();
                        for (int i = 0; i < selected.size(); i++) {
                            int position = selected.keyAt(i);
                            if (selected.valueAt(i)) {
                                // grab the selected friends from the original list (inst of filteredList)
                                RowFriend item = adapter.getItemFromOriginalList(adapter.getOriginalIndex(position));
                                selectedFriends.add(item);
                            }
                        }

                        updateSelectedFriends(selectedFriends);

                        Log.d(TAG, "Selected friends: " + String.valueOf(selectedFriends.size()));
                    }
                })
                .dismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
//                        showingDialog = false;
                    }
                })
                .show();
        dialog.show();
    }

    private void initViews(View v) {
        listView = (ListView) v.findViewById(R.id.lv_selected_friends);
        fabSelectFriends = (ButtonFloat) v.findViewById(R.id.fab_select_friends);
        fabSelectFriends.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (retrievedFriends != null && retrievedFriends.size() > 0) {
                    // if friend list from G+ is available display in the dialog
                    showChooseFriendsDialog(retrievedFriends);
                } else {
                    // if not available, fetch friend list from G+
                    retrieveFriendsFromGPlus();
                }
            }
        });

        adapter = new SelectedFriendListAdapter(mActivity, new ArrayList<RowFriend>());
        listView.setAdapter(adapter);
        listView.setItemsCanFocus(false);
        listView.setChoiceMode(ListView.CHOICE_MODE_NONE);
    }

    /**
     * Update the fragment listview with the friends selected from the dialog
     * Also update the controller with the current list
     *
     * @param selectedFriends
     */
    private void updateSelectedFriends(List<RowFriend> selectedFriends) {
        adapter = new SelectedFriendListAdapter(mActivity, selectedFriends);
        listView.setAdapter(null);
        listView.setAdapter(adapter);

        controller.setFriends(selectedFriends);
    }

    /**
     * If GoogleApiClient is connected, make a call to get friends list
     * <p/>
     * List of Google+ Person model
     * <p/>
     * https://developer.android.com/reference/com/google/android/gms/plus/model/people/Person.html
     */
    private void retrieveFriendsFromGPlus() {

        GoogleApiClient mGoogleApiClient = mActivity.getGoogleApiClient();
        if (mGoogleApiClient.isConnected()) {

            Plus.PeopleApi.loadVisible(mGoogleApiClient, null)
                    .setResultCallback(new ResultCallback<People.LoadPeopleResult>() {
                        @Override
                        public void onResult(People.LoadPeopleResult loadPeopleResult) {
                            if (loadPeopleResult.getStatus().getStatusCode() == CommonStatusCodes.SUCCESS) {
                                PersonBuffer personBuffer = loadPeopleResult.getPersonBuffer();
                                retrievedFriends = new ArrayList<RowFriend>();
                                try {
                                    int count = personBuffer.getCount();
                                    for (int i = 0; i < count; i++) {
                                        if (personBuffer.get(i) != null) {
                                            Log.d(TAG, "Display name: " + personBuffer.get(i).getDisplayName());
                                            RowFriend friend = new RowFriend(personBuffer.get(i).freeze());
                                            retrievedFriends.add(friend);
                                        }
                                    }
                                } finally {
                                    personBuffer.close();
                                    if (retrievedFriends.size() > 0) {
                                        controller.setFriends(retrievedFriends);
//                                        Log.d(TAG, "# Friends: " + friends.size());
                                        showChooseFriendsDialog(retrievedFriends);
                                    }
                                }
                            } else {
                                Log.e(TAG, "Error requesting visible circles: " + loadPeopleResult.getStatus());
                            }
                        }
                    });
        }
    }

    /**
     * takes in relative position to be animated to
     *
     * @param position
     */
    public void moveFloatButton(float position) {
        fabSelectFriends.animate().translationY(position);
    }

}
