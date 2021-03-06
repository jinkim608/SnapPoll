package dev.jinkim.snappollandroid.ui.newpoll;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.util.Log;
import android.widget.Toast;

import com.google.gson.Gson;

import java.io.File;
import java.util.List;

import dev.jinkim.snappollandroid.R;
import dev.jinkim.snappollandroid.app.App;
import dev.jinkim.snappollandroid.imgur.ImgurConstants;
import dev.jinkim.snappollandroid.model.ImgurResponse;
import dev.jinkim.snappollandroid.model.Poll;
import dev.jinkim.snappollandroid.model.PollAttribute;
import dev.jinkim.snappollandroid.ui.invitefriends.RowFriend;
import dev.jinkim.snappollandroid.model.User;
import dev.jinkim.snappollandroid.ui.polldetail.PollDetailActivity;
import dev.jinkim.snappollandroid.util.ColorUtil;
import dev.jinkim.snappollandroid.util.efilechooser.FileUtils;
import dev.jinkim.snappollandroid.web.ImgurRestClient;
import dev.jinkim.snappollandroid.web.SnapPollRestClient;
import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.client.Response;
import retrofit.mime.TypedFile;

/**
 * Created by Jin on 3/8/15.
 *
 * Keep up with the information in creating a new poll flow
 */
public class NewPollController {

    public static final String TAG = NewPollController.class.getSimpleName();

    public int pollId = -1;
    public String question;
    public String title;
    public List<PollAttribute> attributes;
    public boolean multipleResponseAllowed;
    private List<RowFriend> friends;

    private ImgurResponse currentImgurResponse;

    private NewPollActivity mActivity;

    public NewPollController(NewPollActivity activity) {
        mActivity = activity;
    }

    public int getPollId() {
        return pollId;
    }

    public void setPollId(int pollId) {
        this.pollId = pollId;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<PollAttribute> getAttributes() {
        return attributes;
    }

    public void setAttributes(List<PollAttribute> attributes) {
        this.attributes = attributes;
    }

    public boolean isMultipleResponseAllowed() {
        return multipleResponseAllowed;
    }

    public void setMultipleResponseAllowed(boolean multipleResponseAllowed) {
        this.multipleResponseAllowed = multipleResponseAllowed;
    }

    public void setFriends(List<RowFriend> friends) {
        this.friends = friends;
    }

    public List<RowFriend> getFriends() {
        return friends;
    }

    public boolean isImageUploaded() {
        return (currentImgurResponse != null);
    }

    public boolean isPollUploaded() {
        return (pollId != -1);
    }

    public void uploadImage() {

        mActivity.showProgressBar(R.string.msg_submitting);

        // if the image is already uploaded
        if (currentImgurResponse != null) {
            submitPoll(currentImgurResponse);

        } else {
            //TODO: Check if this util works with various versions of Android

            File file = null;
            if (mActivity.getSelectedImageUri() != null) {
                file = FileUtils.getFile(mActivity, mActivity.getSelectedImageUri());
            } else if (mActivity.getCapturedPhotoPath() != null) {
                file = new File(mActivity.getCapturedPhotoPath());
            }

            // check the file object created
            if (file == null) {
                Toast.makeText(mActivity, "Error in creating the image file.", Toast.LENGTH_SHORT).show();
                return;
            }

            //TODO: Catch exception and handle it
            TypedFile tf = new TypedFile("image/*", file);

            ImgurRestClient imgurRest = new ImgurRestClient();
            imgurRest.getApiService().uploadImage("Client-ID " + ImgurConstants.MY_IMGUR_CLIENT_ID,
                    tf, title, mActivity.getString(R.string.image_description),
                    new Callback<ImgurResponse>() {
                        @Override
                        public void success(ImgurResponse imgurResponse, Response response) {
                            currentImgurResponse = imgurResponse;
                            Log.d(TAG, "Success: Image uploaded to Imgur");
                            Log.d(TAG, "Imgur link: " + currentImgurResponse.data.link);
                            Log.d(TAG, "Imgur deleteHash: " + currentImgurResponse.data.deletehash);

                            submitPoll(currentImgurResponse);
                        }

                        @Override
                        public void failure(RetrofitError error) {
                            Log.d(TAG, "Failure: Image not uploaded to Imgur");
                            // TODO: ERROR MESSAGE
                            mActivity.hideProgressBar();
                            mActivity.setSubmitting(false);
                        }
                    });
        }
    }

    private void submitPoll(ImgurResponse resp) {

        if (!resp.success) {
            // TODO: HANDLE ERROR
        }
        User u = App.getInstance().getCurrentUser(mActivity);

        Poll currentPoll = new Poll();
        currentPoll.setCreatorId(u.getUserId());
        currentPoll.setQuestion(question);
        currentPoll.setTitle(title);
        currentPoll.setActive(true);
        currentPoll.setMultipleResponseAllowed(isMultipleResponseAllowed());
        currentPoll.setReferenceUrl(resp.data.getLink());
        currentPoll.setReferenceDeleteHash(resp.data.getDeletehash());

        // if attribute is empty, add a default attribute
        if (attributes.size() < 1) {
            // set a default attribute
            PollAttribute at = new PollAttribute();
            at.setAttributeName(mActivity.getString(R.string.attribute_name_default));
            String colorHex = ColorUtil.convertToHex(mActivity.getResources().getColor(R.color.app_primary));
            at.setAttributeColorHex(colorHex);
            attributes.add(at);
        }
        currentPoll.setAttributes(attributes);

        SnapPollRestClient.ApiService rest = new SnapPollRestClient().getApiService();

        rest.createPoll(currentPoll, new Callback<Poll>() {
            @Override
            public void success(Poll poll, Response response) {
                int pollId = poll.getPollId();
                Log.d(TAG, "Success: pollId: " + pollId + " uploaded to SnapPoll database");

                mActivity.hideProgressBar();
                mActivity.setSubmitting(false);
                poll.setAttributes(attributes);

                Gson gson = new Gson();
                String pollJsonString = gson.toJson(poll).toString();

                Intent intent = new Intent(mActivity, PollDetailActivity.class);
                intent.putExtra(mActivity.getString(R.string.key_poll_id), pollId);
                intent.putExtra(mActivity.getString(R.string.key_poll), pollJsonString);
                intent.putExtra(mActivity.getString(R.string.key_show_poll_created_msg), true);
                intent.putExtra(mActivity.getString(R.string.key_view_result_mode), true);
                mActivity.startActivity(intent);
                mActivity.finish();
            }

            @Override
            public void failure(RetrofitError error) {
                // TODO: failure message for user
                Log.d(TAG, "Failure: Poll not uploaded: " + error.toString());
                mActivity.setSubmitting(false);
            }
        });
    }

    public static class AttributeLineItem {
        private String attributeColorHex;
        private String attributeName;

        public String getAttributeColorHex() {
            return attributeColorHex;
        }

        public void setAttributeColorHex(String attributeColorHex) {
            this.attributeColorHex = attributeColorHex;
        }

        public String getAttributeName() {
            return attributeName;
        }

        public void setAttributeName(String attributeName) {
            this.attributeName = attributeName;
        }

        public int getColorInt(Context context) {
            int color;

            try {
                color = Color.parseColor(attributeColorHex);
            } catch (IllegalArgumentException e) {
                color = context.getResources().getColor(R.color.app_primary);
            }
            return color;
        }
    }
}
