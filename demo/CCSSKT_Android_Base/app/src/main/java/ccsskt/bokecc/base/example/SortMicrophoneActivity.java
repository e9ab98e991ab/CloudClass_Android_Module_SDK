package ccsskt.bokecc.base.example;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.Looper;
import android.os.Message;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.SpannableString;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bokecc.sskt.base.CCAtlasCallBack;
import com.bokecc.sskt.base.CCAtlasClient;
import com.bokecc.sskt.base.CCStream;
import com.bokecc.sskt.base.LocalStreamConfig;
import com.bokecc.sskt.base.SubscribeRemoteStream;
import com.bokecc.sskt.base.bean.CCInteractBean;
import com.bokecc.sskt.base.bean.CCUser;
import com.bokecc.sskt.base.exception.StreamException;
import com.bokecc.sskt.base.renderer.CCSurfaceRenderer;
import com.example.ccbarleylibrary.CCBarLeyManager;
import com.example.ccbarleylibrary.CCBarLeyCallBack;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.RendererCommon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.CopyOnWriteArrayList;

import butterknife.BindView;
import butterknife.OnClick;
import ccsskt.bokecc.base.example.adapter.VideoAdapter;
import ccsskt.bokecc.base.example.base.BaseActivity;
import ccsskt.bokecc.base.example.bean.VideoStreamView;

import static com.bokecc.sskt.base.CCAtlasClient.LIANMAI_MODE_AUTO;
import static com.bokecc.sskt.base.CCAtlasClient.LIANMAI_MODE_FREE;
import static com.bokecc.sskt.base.CCAtlasClient.LIANMAI_MODE_NAMED;

/**
 * @author CC视频
 * @Date: on 2018/7/9.
 * @Email: houbs@bokecc.com
 * 连麦相关
 */
public class SortMicrophoneActivity extends BaseActivity {
    @BindView(R.id.id_student_handup)
    Button mHandup;
    @BindView(R.id.id_student_lianmaistyle)
    Button mLianmaiStyle;
    @BindView(R.id.id_student_bottom_layout)
    RelativeLayout mBottomLayout;
    @BindView(R.id.tv_start)
    TextView tvStart;
    @BindView(R.id.id_student_recycler)
    RecyclerView mRecyclerView;

    private CCBarLeyManager barLeyManager;//排麦组件

    private int mMaiStatus = 0;  // 连麦状态
    private boolean isAutoHandup = false; //自动连麦模式 举手

    // 更新连麦按钮状态
    private int mQueueIndex;
    private static final int MAI_STATUS_NORMAL = 0;
    private static final int MAI_STATUS_QUEUE = 1;
    private static final int MAI_STATUS_ING = 2;

    //流相关
    private VideoAdapter mVideoAdapter;
    private CopyOnWriteArrayList<VideoStreamView> mVideoStreamViews = new CopyOnWriteArrayList<>();
    private CCStream mLocalStream;
    private DrawHandler mDrawHandler;
    //跳转后的参数
    private static final String KEY_SESSION_ID = "session_id";
    private static final String KEY_USER_ACCOUNT = "user_account";
    private static final String KEY_ROOM_ID = "room_id";

    //监听流相关
    CCAtlasClient.OnNotifyStreamListener onNotifyStreamListener = new CCAtlasClient.OnNotifyStreamListener() {
        @Override
        public void onStreamAllowSub(final SubscribeRemoteStream stream) {
            // 监听添加远程流;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    addVideoView(stream);
                }
            });
        }

        @Override
        public void onStreamRemoved(final SubscribeRemoteStream stream) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    //监听删除远程流
                    removeStreamView(stream);
                }
            });
        }

        @Override
        public void onStreamError() {

        }
    };


    public static void startSelf(Context context, String sessionid, String roomid, String userAccount) {
        context.startActivity(newIntent(context, sessionid, roomid, userAccount));
    }

    private static Intent newIntent(Context context, String sessionid, String roomid, String userAccount) {
        Intent intent = new Intent(context, SortMicrophoneActivity.class);
        intent.putExtra(KEY_SESSION_ID, sessionid);
        intent.putExtra(KEY_USER_ACCOUNT, userAccount);
        intent.putExtra(KEY_ROOM_ID, roomid);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return intent;
    }

    @Override
    protected int getLayoutId() {
        return R.layout.activity_sort_microphone;
    }

    @Override
    protected void onViewCreated() {

        //初始化基础类
        barLeyManager = CCBarLeyManager.getInstance();
        barLeyManager.setOnNotifyStreamListener(onNotifyStreamListener);
        mDrawHandler = new DrawHandler(Looper.getMainLooper());
        //recyclerView的设置
        mVideoAdapter = new VideoAdapter(this);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
        linearLayoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
        mRecyclerView.setLayoutManager(linearLayoutManager);
        mRecyclerView.setAdapter(mVideoAdapter);
        //调用join 方法
        final String sessionid = getIntent().getStringExtra(KEY_SESSION_ID);
        final String userAccount = getIntent().getStringExtra(KEY_USER_ACCOUNT);
        String rtmp = "rtmp://push-cc1.csslcloud.net/origin/" + getIntent().getStringExtra(KEY_ROOM_ID);
        showProgress();
        //开始进入房间
        ccAtlasClient.join(sessionid, userAccount, null, new CCAtlasCallBack<CCInteractBean>() {
            @Override
            public void onSuccess(CCInteractBean ccInteractBean) {
                dismissProgress();
                showToast("join room success");
                subStream();
                isStartLiving();
                if (ccAtlasClient.getUserList() != null) {
                    for (CCUser user :
                            ccAtlasClient.getUserList()) {
                        if (user.getLianmaiStatus() == ccAtlasClient.LIANMAI_STATUS_IN_MAI) {
                            Log.i("wdh---?", "wdh------>onSuccess: " + user.getUserName());
                        }
                    }
                }
            }

            @Override
            public void onFailure(int errCode, String errMsg) {
                dismissProgress();
                showToast(errMsg);
            }
        });

        //监听直播状态
        ccAtlasClient.setOnClassStatusListener(onClassStatusListener);
        ccAtlasClient.setOnMediaListener(new CCAtlasClient.OnMediaListener() {
            @Override
            public void onAudio(String userid, boolean isAllowAudio, boolean isSelf) {

            }

            @Override
            public void onVideo(String userid, boolean isAllowVideo, boolean isSelf) {

            }
        });
        //web 设置更改连麦模式后,APP接收状态013，对应自由连麦、举手、自动连麦
        barLeyManager.setOnSpeakModeUpdateListener(onLianmaiModeUpdateListener);
        //自由连麦和举手连麦会有，麦序的更新
        barLeyManager.setOnQueueMaiUpdateListener(onQueueMaiUpdateListener);
        //监听上麦、下麦
        barLeyManager.setOnNotifyMaiStatusLisnter(onNotifyMaiStatusLisnter);
        //举手状态时 ，监听老师的邀请还是取消邀请
        barLeyManager.setOnNotifyInviteListener(onNotifyInviteListener);
        //学员取消举手连麦
        barLeyManager.setOnCancelHandUpListener(onCancelHandUpListener);
        //用户自己定义的socket事件
        ccAtlasClient.setOnPublishMessageListener(mPublishMessage);
        //踢出房间
        barLeyManager.setOnKickOutListener(new CCBarLeyManager.OnKickOutListener() {
            @Override
            public void onKickOut() {
                showToast("对不起，您已经被踢出该直播间");
                finish();
                ccAtlasClient.leave(null);
            }
        });
    }

    private void message(final String userName){
        Message msg = new Message();
        msg.obj = userName;
        msg.what = 1;
        mDrawHandler.sendMessage(msg);

    }
    private final class DrawHandler extends android.os.Handler {

        DrawHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            pusher(msg.obj.toString());
        }
    }

    private void pusher(String str){
        Toast toast = Toast.makeText(this, str, Toast.LENGTH_LONG);
        toast.setGravity(Gravity.CENTER, 0, 0);
        toast.show();
    }

    //举手点击事件
    @OnClick(R.id.id_student_handup)
    void autoHandup() {
        if (ccAtlasClient.getInteractBean().getLianmaiMode() == CCAtlasClient.LIANMAI_MODE_NAMED) {
            requestMai();//如果是举手模式就去连麦
        } else { //只有自动连麦才可以走
            //举手和取消举手，根据传的true和false,老师接收你的举手消息
            barLeyManager.Studenthandup(!isAutoHandup, new CCBarLeyCallBack<Void>() {
                @Override
                public void onSuccess(Void aVoid) {
                    //举手和取消举手成功
                    isAutoHandup = !isAutoHandup;
                    mHandup.setBackgroundResource(isAutoHandup ? R.drawable.handup_cancel_selector : R.drawable.handup_selector);
                }

                @Override
                public void onFailure(String err) {
                    showToast(err);
                }
            });
        }

    }

    //连麦点击事件
    @OnClick(R.id.id_student_lianmaistyle)
    void requestMai() {
        barLeyManager.handsup(new CCBarLeyCallBack<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                //如果连麦中的状态，就下麦停止推流，否则就排麦上麦推流
                if (mMaiStatus == MAI_STATUS_ING) {
                    barLeyManager.handsDown(new CCBarLeyCallBack<Void>() {
                        @Override
                        public void onSuccess(Void aVoid) {
                            showToast("下麦成功");
//                            unpublish();//停止推流
                            updateMaiButton(MAI_STATUS_NORMAL);//更新原始状态
                        }

                        @Override
                        public void onFailure(String err) {
                        }
                    });
                } else {
                    mQueueIndex = -1;
                    updateMaiButton(MAI_STATUS_QUEUE);
                }
            }

            @Override
            public void onFailure(String err) {
                toastOnUiThread(err);
            }
        });
    }
    //举手连麦模式，取消举手连麦
    @OnClick(R.id.id_student_cancelHandUp)
    void cancelHandUp() {
        barLeyManager.handsUpCancel(new CCBarLeyCallBack<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                mLianmaiStyle.setBackgroundResource(R.drawable.queue_mai_selector);
            }

            @Override
            public void onFailure(String err) {

            }
        });
    }
    //join
    CCAtlasCallBack<CCInteractBean> ccAtlasCallBack = new CCAtlasCallBack<CCInteractBean>() {
        @Override
        public void onSuccess(CCInteractBean ccBaseBean) {
            dismissProgress();
            showToast("join room success");
            subStream();
            isStartLiving();
        }
        @Override
        public void onFailure(int errCode, String errMsg) {
            dismissProgress();
            showToast(errMsg);
        }
    };

    private void subStream(){
        if(ccAtlasClient.isRoomLive()){
            CopyOnWriteArrayList<SubscribeRemoteStream> streams = ccAtlasClient.getSubscribeRemoteStreams();
            for (SubscribeRemoteStream stream :
                    streams) {
                addVideoView(stream);
            }
        }
    }

    private void isStartLiving(){
        //是不是开始直播
        if (ccAtlasClient.isRoomLive()) {
            tvStart.setVisibility(View.GONE);
            // 如果开启了，并且是自动连麦模式，显示举手和连麦按钮，且连麦按钮不可点击
            if (ccAtlasClient.getInteractBean().getLianmaiMode() == LIANMAI_MODE_AUTO) {
                mHandup.setVisibility(View.VISIBLE);
                mLianmaiStyle.setVisibility(View.VISIBLE);
                mLianmaiStyle.setClickable(false);//自动连麦不可点击
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        requestMai();
                    }
                }).start();

            } else {
                mHandup.setVisibility(View.GONE);
                mLianmaiStyle.setClickable(true);//其他可以可点击
            }
        } else {
            tvStart.setVisibility(View.VISIBLE);
            //结束直播，显示举手，连麦按钮隐藏
            if (ccAtlasClient.getInteractBean().getLianmaiMode() == LIANMAI_MODE_AUTO) {
                mHandup.setVisibility(View.VISIBLE);
                mLianmaiStyle.setVisibility(View.GONE);
            }
        }
    }
    //监听直播状态
    CCAtlasClient.OnClassStatusListener onClassStatusListener = new CCAtlasClient.OnClassStatusListener() {
        @Override
        public void onStart() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    tvStart.setVisibility(View.GONE);
                    // 如果开启了，并且是自动连麦模式，显示举手和连麦按钮，且连麦按钮不可点击
                    if (ccAtlasClient.getInteractBean().getLianmaiMode() == LIANMAI_MODE_AUTO) {
                        mHandup.setVisibility(View.VISIBLE);
                        mLianmaiStyle.setVisibility(View.VISIBLE);
                        mLianmaiStyle.setClickable(false);//自动连麦不可点击
                        requestMai();
                    } else {
                        mHandup.setVisibility(View.GONE);
                        mLianmaiStyle.setClickable(true);//其他可以可点击
                    }
                }
            });
        }

        @Override
        public void onStop() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    tvStart.setVisibility(View.VISIBLE);
                    updateMaiButton(MAI_STATUS_NORMAL);
                    if (mMaiStatus == MAI_STATUS_ING) {
                        unpublish();
                    }
                }
            });
        }
    };
    //web 设置更改连麦模式后,APP接收状态013，对应自由连麦、举手、自动连麦
    CCBarLeyManager.OnSpeakModeUpdateListener onLianmaiModeUpdateListener = new CCBarLeyManager.OnSpeakModeUpdateListener() {
        @Override
        public void onBarLeyMode(final int mode) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mLianmaiStyle.setVisibility(View.VISIBLE);
                    if (mode == LIANMAI_MODE_AUTO || mode == LIANMAI_MODE_NAMED) {
                        mHandup.setVisibility(View.VISIBLE);
                        if (mMaiStatus == CCAtlasClient.LIANMAI_STATUS_MAI_ING) {
                            updateMaiButton(CCAtlasClient.LIANMAI_STATUS_MAI_ING);
                        } else {
                            mLianmaiStyle.setVisibility(View.GONE);
                        }
                    } else {
                        mHandup.setVisibility(View.GONE);
                        // 更新连麦按钮
                        updateMaiButton(mMaiStatus);
                    }
                }
            });
        }
    };
    // 麦排序的相关处理
    CCBarLeyManager.OnQueueMaiUpdateListener onQueueMaiUpdateListener = new CCBarLeyManager.OnQueueMaiUpdateListener() {
        @Override
        public void onUpdateBarLeyStatus(ArrayList<CCUser> users) {
            Log.i("wdh--->", "onUpdateBarLeyStatus: " + users);
            if(ccAtlasClient.getInteractBean() != null){
                if (ccAtlasClient.getInteractBean().getLianmaiMode() == LIANMAI_MODE_NAMED) { // 点名连麦模式

                } else { // 自由连麦模式
                    if (mMaiStatus == 1) { // 如果麦序发生变化 并且当前用户在麦序中 进行麦序计算
                        sortUser(users);
                    }
                }
            }
        }
    };

    //监听上下麦监听处理
    CCBarLeyManager.OnNotifyMaiStatusLisnter onNotifyMaiStatusLisnter = new CCBarLeyManager.OnNotifyMaiStatusLisnter() {
        @Override
        public void onUpMai(int oldStatus) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    publish();
                }
            });
        }

        @Override
        public void onDownMai() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    unpublish();
                    barLeyManager.handsDown(new CCBarLeyCallBack<Void>() {
                        @Override
                        public void onSuccess(Void aVoid) {

                        }

                        @Override
                        public void onFailure(String err) {

                        }
                    });
                }
            });
        }
    };

    //老师邀请相关处理
    CCBarLeyManager.OnNotifyInviteListener onNotifyInviteListener = new CCBarLeyManager.OnNotifyInviteListener() {
        @Override
        public void onInvite() {
            //老师邀请
            final AlertDialog.Builder builder = new AlertDialog.Builder(SortMicrophoneActivity.this);
            builder.setTitle("邀请");
            builder.setMessage("是否同意老师邀请");
            builder.setPositiveButton("同意", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(final DialogInterface dialog, int which) {
                    //老师邀请上麦
                    barLeyManager.acceptTeacherInvite(new CCBarLeyCallBack<Void>() {
                        @Override
                        public void onSuccess(Void aVoid) {
                            showToast("同意老师邀请");
                            dialog.cancel();
                        }

                        @Override
                        public void onFailure(String err) {
                            showToast(err);
                        }
                    });
                }
            }).setNegativeButton("取消", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(final DialogInterface dialog, int which) {
                    // 拒绝老师连麦
                    barLeyManager.refuseTeacherInvite(new CCBarLeyCallBack<Void>() {
                        @Override
                        public void onSuccess(Void aVoid) {
                            showToast("拒绝老师邀请");
                            dialog.cancel();
                        }

                        @Override
                        public void onFailure(String err) {
                            showToast(err);
                        }
                    });
                }
            });
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    builder.create().show();
                }
            });
        }

        @Override
        public void onCancel() {
            //老师取消邀请
        }
    };
    //学员取消举手
    CCBarLeyManager.OnCancelHandUpListener onCancelHandUpListener = new CCBarLeyManager.OnCancelHandUpListener() {
        @Override
        public void OnCancelHandUp(String userId, String userName) {
            Log.i("wdh", "onSuccess: " + userName);
            JSONObject data = new JSONObject();
            try {
                data.put("userid",userId);
                data.put("username",userName);
                ccAtlasClient.sendPublishMessage(data);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    };

    //用户监听自己设置的socket事件
    private CCAtlasClient.OnPublishMessageListener mPublishMessage = new CCAtlasClient.OnPublishMessageListener() {
        @Override
        public void onPublishMessage(JSONObject object) {
            try {
                message(object.getString("username") + "取消举手");
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    };

    //更新连麦状态
    private void updateMaiButton(final int status) {

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mLianmaiStyle.setVisibility(View.VISIBLE);
                mLianmaiStyle.setText("");
                mMaiStatus = status;
                switch (status) {
                    case MAI_STATUS_ING:
                        showToast("连麦成功");
                        mLianmaiStyle.setBackgroundResource(R.drawable.maiing_selector);
                        mLianmaiStyle.setVisibility(View.VISIBLE);
                        break;
                    case MAI_STATUS_NORMAL:
                        if(ccAtlasClient.getInteractBean() != null){
                            if (mHandup.getVisibility() == View.VISIBLE) {
                                isAutoHandup = false;
                                mHandup.setBackgroundResource(R.drawable.handup_selector);
                            }
                            if (ccAtlasClient.getInteractBean().getLianmaiMode() == LIANMAI_MODE_FREE) {
                                mLianmaiStyle.setBackgroundResource(R.drawable.queue_mai_selector);
                            } else if (ccAtlasClient.getInteractBean().getLianmaiMode() == LIANMAI_MODE_AUTO) {
                                mLianmaiStyle.setVisibility(View.GONE);//自动连麦的时候不可以点击
                            } else if (ccAtlasClient.getInteractBean().getLianmaiMode() == CCAtlasClient.LIANMAI_MODE_NAMED) {
                                mLianmaiStyle.setBackgroundResource(R.drawable.queue_mai_selector);
                            }
                        }
                        break;
                    case MAI_STATUS_QUEUE:
                        if (ccAtlasClient.getInteractBean().getLianmaiMode() == LIANMAI_MODE_FREE) {
                            mLianmaiStyle.setTextColor(Color.WHITE);
                            if (mQueueIndex == -1) {
                                mLianmaiStyle.setText("    排麦中");
                            } else {
                                SpannableString maiStr = new SpannableString("    排麦中\n    第" + mQueueIndex + "位");
                                mLianmaiStyle.setText(maiStr);
                            }
                            mLianmaiStyle.setBackgroundResource(R.drawable.queuing_selector);
                        } else if (ccAtlasClient.getInteractBean().getLianmaiMode() == CCAtlasClient.LIANMAI_MODE_NAMED) {
                            mHandup.setBackgroundResource(R.drawable.handup_cancel_selector);
                        } else {
                            mLianmaiStyle.setBackgroundResource(R.drawable.handup_cancel_selector);
                        }
                        break;
                }
            }
        });
    }

    //用户排序
    private void sortUser(ArrayList<CCUser> users) {
        ArrayList<CCUser> compareUsers = new ArrayList<>();
        for (CCUser user :
                users) {
            if (user.getLianmaiStatus() == CCAtlasClient.LIANMAI_STATUS_IN_MAI ||
                    user.getLianmaiStatus() == CCAtlasClient.LIANMAI_STATUS_UP_MAI) {
                compareUsers.add(user);
            }
        }
        Collections.sort(compareUsers, new UserComparator());
        mQueueIndex = 1;
        for (CCUser user :
                compareUsers) {
            if (user.getUserId().equals(ccAtlasClient.getUserIdInPusher())) {
                updateMaiButton(MAI_STATUS_QUEUE);
                break;
            }
            mQueueIndex++;
        }
    }

    //用户对比
    public class UserComparator implements Comparator<CCUser> {

        @Override
        public int compare(CCUser o1, CCUser o2) {
            long curRequestTime = (long) Double.parseDouble(o1.getRequestTime());
            long compareRequestTime = (long) Double.parseDouble(o2.getRequestTime());
            return (int) (curRequestTime - compareRequestTime);
        }
    }

    //推流
    private synchronized void publish() {
        createLocalStream();//生成本地流
        ccAtlasClient.publish(new CCAtlasCallBack<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                //推流成功，更新按钮“连接中”状态
                updateMaiButton(MAI_STATUS_ING);
            }

            @Override
            public void onFailure(int errCode, final String errMsg) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        //如果是自动连麦隐藏按钮
                        if (ccAtlasClient.getInteractBean().getLianmaiMode() == LIANMAI_MODE_AUTO) {
                            mLianmaiStyle.setVisibility(View.GONE);
                        }
                        showToast(errMsg);
                        closeLocalCameraStream();
                    }
                });
            }
        });
    }

    //停止推流
    private synchronized void unpublish() {
        ccAtlasClient.unpublish(new CCAtlasCallBack<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                updateList4Unpublish();
            }

            @Override
            public void onFailure(int errCode, String errMsg) {
                toastOnUiThread(errMsg);
                updateList4Unpublish();
            }
        });
    }

    //停止推流
    private void updateList4Unpublish() {
        closeLocalCameraStream();
        updateMaiButton(MAI_STATUS_NORMAL);

    }

    //创建本地流
    private void createLocalStream() {
        LocalStreamConfig config = new LocalStreamConfig.LocalStreamConfigBuilder().build();
        try {
            //本地数据流
            ccAtlasClient.createLocalStream(ccAtlasClient.getMediaMode());
        } catch (StreamException e) {
            showToast(e.getMessage());
        }
    }

    /**
     * 关闭本地流
     */
    public void closeLocalCameraStream() {
        if (ccAtlasClient == null) {
            return;
        }
        ccAtlasClient.destoryLocalStream();
    }

    //添加流
    private synchronized void addVideoView(final SubscribeRemoteStream mStream) {
        //临时渲染器
        final CCSurfaceRenderer mRemoteMixRenderer;
        mRemoteMixRenderer = new CCSurfaceRenderer(this);
        mRemoteMixRenderer.init(CCAtlasClient.getInstance().getEglBase().getEglBaseContext(), null);
        mRemoteMixRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        try {
            //开始订阅
            ccAtlasClient.SubscribeStream(mStream.getRemoteStream(), new CCAtlasCallBack<CCStream>() {
                @Override
                public void onSuccess(CCStream ccStream) {
                    //生成VideoStreamView对象
                    VideoStreamView videoRemoteStreamView = new VideoStreamView();
                    videoRemoteStreamView.setRenderer(mRemoteMixRenderer);
                    SubscribeRemoteStream tempStream = new SubscribeRemoteStream();
                    tempStream.setRemoteStream(ccStream);
                    videoRemoteStreamView.setStream(tempStream);
                    //添加流到数组里
                    mVideoStreamViews.add(videoRemoteStreamView);
                    //绑定数据
                    mVideoAdapter.bindDatas(mVideoStreamViews);
                    //循环渲染刷新
                    for (int i = 0; i < mVideoStreamViews.size(); i++) {
                        try {
                            //数据流绑定渲染器
                            mVideoStreamViews.get(i).getStream().attach(mVideoStreamViews.get(i).getRenderer());
                            //刷新列表
                            mVideoAdapter.notifyItemRangeChanged(i, mVideoStreamViews.size() - i);
                        } catch (StreamException e) {
                            e.printStackTrace();
                        }
                    }
                }

                @Override
                public void onFailure(int errCode, String errMsg) {

                }
            });
        } catch (StreamException e) {
            e.printStackTrace();
        }

    }

    //删除流
    private synchronized void removeStreamView(SubscribeRemoteStream stream) {
        VideoStreamView tempView = null;
        int position = -1;
        //循环找到对应的数据流
        for (int i = 0; i < mVideoStreamViews.size(); i++) {
            VideoStreamView streamView = mVideoStreamViews.get(i);
            if (streamView.getStream().getStreamId().equals(stream.getStreamId())) {
                tempView = streamView;
                position = i;
                break;
            }
        }

        mVideoStreamViews.remove(tempView);
        try {
            //取消订阅
            ccAtlasClient.unSubscribeStream(stream.getRemoteStream(), null);
        } catch (StreamException e) {
            e.printStackTrace();
        }
        mVideoAdapter.notifyItemChanged(position);
        stream.getRemoteStream().detach();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        if (mMaiStatus == MAI_STATUS_ING) {
            unpublish();
        }
        //离开房间
        ccAtlasClient.leave(null);
        //断开socket
        ccAtlasClient.disconnectSocket();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        finish();
    }
}
