package dev.jinkim.snappollandroidclient.model;

import java.util.Date;

/**
 * Created by Jin on 11/23/14.
 */
public class Poll {
    public int pollId;
    public String creatorId;
    public String title;
    public String question;
    public Date pollTimestamp;
    public boolean active;
    public boolean multipleResponseAllowed;

}
