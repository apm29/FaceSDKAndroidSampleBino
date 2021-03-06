package com.baidu.idl.sample.view;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.baidu.idl.facesdk.utils.PreferencesUtil;
import com.baidu.idl.sample.R;
import com.baidu.idl.sample.adapter.CameraPagerAdapter;
import com.baidu.idl.sample.callback.ILivenessCallBack;
import com.baidu.idl.sample.manager.FaceSDKManager;
import com.baidu.idl.sample.model.LivenessModel;
import com.baidu.idl.sample.utils.Utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.baidu.idl.sample.common.GlobalSet.PERFER_HEIGH;
import static com.baidu.idl.sample.common.GlobalSet.PREFER_WIDTH;
import static com.baidu.idl.sample.common.GlobalSet.TYPE_PREVIEW_ANGLE;
import static com.baidu.idl.sample.common.GlobalSet.TYPE_TPREVIEW_NINETY_ANGLE;

/**
 * Created by litonghui on 2018/12/8.
 */

public class BinocularView extends BaseCameraView implements View.OnClickListener, ILivenessCallBack {
    private Preview[] mPreview;
    private Camera[] mCamera;
    private int mCameraNum;

    private RelativeLayout mLayoutOne, mLayoutTwo;
    private TextureView mTextureViewOne, mTextureViewTwo;
    private SurfaceView mSurfaceOne, mSurfaceTwo;
    private ImageView mBtnGoLeft, mBtnGoRight;
    private ImageView mImageTracker;
    private ImageView mImageTrackerIr;

    private ViewPager mViewPager;
    private CameraPagerAdapter mPagerAdapter;

    private volatile int[] niRargb;
    private volatile int[] rgbData;
    private volatile byte[] irData;
    private int camemra1DataMean;
    private int camemra2DataMean;
    private volatile boolean camemra1IsRgb = false;
    private volatile boolean rgbOrIrConfirm = false;

    private Context context;

    private ILivenessCallBack livenessCallBack;
    private int previewAngle;
    private int trackAngle;
    private int mTrackImageWidth;
    private int mTrackImageHeight;


    public BinocularView(Context context) {
        this(context, null);
    }

    public BinocularView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BinocularView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initview(context);
        initFaceFrame(context);
    }

    public void setLivenessCallBack(ILivenessCallBack livenessCallBack) {
        this.livenessCallBack = livenessCallBack;
    }

    public void setImageView(ImageView imageView) {
        mImageTracker = imageView;
    }

    public void setIrImageView(ImageView imageView) {
        mImageTrackerIr = imageView;
    }

    private void initview(Context context) {
        this.context = context;

        LayoutInflater.from(getContext()).inflate(R.layout.layout_binocular, this);
        mViewPager = findViewById(R.id.viewpage);

        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);

        mLayoutOne = new RelativeLayout(context);
        mLayoutTwo = new RelativeLayout(context);

        List<View> viewList = new ArrayList<>();
        viewList.add(mLayoutOne);
        viewList.add(mLayoutTwo);
        mPagerAdapter = new CameraPagerAdapter(viewList);
        mViewPager.setAdapter(mPagerAdapter);

        mTextureViewOne = new TextureView(context);
        mTextureViewOne.setOpaque(false);
        mLayoutOne.addView(mTextureViewOne, lp);

        mTextureViewTwo = new TextureView(context);
        mTextureViewTwo.setOpaque(false);
        mLayoutTwo.addView(mTextureViewTwo, lp);


        mSurfaceOne = new SurfaceView(context);
        mLayoutOne.addView(mSurfaceOne, lp);

        mSurfaceTwo = new SurfaceView(context);
        mLayoutTwo.addView(mSurfaceTwo, lp);

        mBtnGoLeft = findViewById(R.id.btn_go_left);
        mBtnGoLeft.setOnClickListener(this);
        mBtnGoRight = findViewById(R.id.btn_go_right);
        mBtnGoRight.setOnClickListener(this);


        mCameraNum = Camera.getNumberOfCameras();
        if (mCameraNum != 2) {
            Toast.makeText(context, "未检测到2个摄像头", Toast.LENGTH_LONG).show();
            return;
        } else {
            mPreview = new Preview[mCameraNum];
            mCamera = new Camera[mCameraNum];
            mPreview[0] = new Preview(context, mSurfaceOne);
            mPreview[1] = new Preview(context, mSurfaceTwo);
        }
        previewAngle = PreferencesUtil.getInt(TYPE_PREVIEW_ANGLE, TYPE_TPREVIEW_NINETY_ANGLE);
        // trackAngle = PreferencesUtil.getInt(TYPE_TRACK_ANGLE, TYPE_TRACK_TWO_HUNDERED_SEVENTY_ANGLE);
        // 为适配在手机中显示的对应正确人脸可以正确检测到，当预览角度是90、270度时，检测角度与之相反，
        // 送检的宽高也相反；
        // 当预览角度是0、180度时，检测角度与之相同，送检的宽高也相同。
        if (previewAngle == 90) {
            trackAngle = 270;
            mTrackImageWidth = PERFER_HEIGH;
            mTrackImageHeight = PREFER_WIDTH;
        } else if (previewAngle == 270) {
            trackAngle = 90;
            mTrackImageWidth = PERFER_HEIGH;
            mTrackImageHeight = PREFER_WIDTH;
        } else {
            trackAngle = previewAngle;
            mTrackImageWidth = PREFER_WIDTH;
            mTrackImageHeight = PERFER_HEIGH;
        }
        FaceSDKManager.getInstance().getFaceLiveness().setLivenessCallBack(this);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.btn_go_left) {
            if (mViewPager != null) {
                mViewPager.setCurrentItem(0);
            }

        } else if (id == R.id.btn_go_right) {
            if (mViewPager != null) {
                mViewPager.setCurrentItem(1);
            }
        }
    }

    public void onResume() {
        if (mCameraNum != 2) {
            Toast.makeText(context, "未检测到2个摄像头", Toast.LENGTH_LONG).show();
            return;
        } else {
            try {
                mCamera[0] = Camera.open(0);
                mCamera[1] = Camera.open(1);
                mCamera[0].setDisplayOrientation(270);
                mCamera[1].setDisplayOrientation(90);
                mPreview[0].setCamera(mCamera[0], PREFER_WIDTH, PERFER_HEIGH);
                mPreview[1].setCamera(mCamera[1], PREFER_WIDTH, PERFER_HEIGH);
                mCamera[0].setPreviewCallback(new Camera.PreviewCallback() {
                    @Override
                    public void onPreviewFrame(byte[] data, Camera camera) {
                        if (rgbOrIrConfirm) {
                            choiceRgbOrIrType(0, data);
                        } else if (camemra1DataMean == 0) {
                            rgbOrIr(0, data);
                        }
                    }
                });
                mCamera[1].setPreviewCallback(new Camera.PreviewCallback() {
                    @Override
                    public void onPreviewFrame(byte[] data, Camera camera) {
                        if (rgbOrIrConfirm) {
                            choiceRgbOrIrType(1, data);
                        } else if (camemra2DataMean == 0) {
                            rgbOrIr(1, data);
                        }

                    }
                });
            } catch (RuntimeException e) {
                // Log.e(TAG, e.getMessage());
            }
        }

        if (mTextureViewOne != null) {
            mTextureViewOne.setOpaque(false);
        }
        if (mTextureViewTwo != null) {
            mTextureViewTwo.setOpaque(false);
        }

    }

    public void onPause() {
        for (int i = 0; i < mCameraNum; i++) {
            if (mCameraNum >= 2) {
                if (mCamera[i] != null) {
                    mCamera[i].setPreviewCallback(null);
                    mCamera[i].stopPreview();
                    mPreview[i].release();
                    mCamera[i].release();
                    mCamera[i] = null;
                }
            }
        }
        // 摄像头重启情况之前记录状态
        FaceSDKManager.getInstance().getFaceLiveness().clearInfo();
    }

    private synchronized void rgbOrIr(int index, byte[] data) {
        byte[] tmp = new byte[PREFER_WIDTH * PERFER_HEIGH];
        try {
            System.arraycopy(data, 0, tmp, 0, PREFER_WIDTH * PERFER_HEIGH);
        } catch (NullPointerException e) {
        }
        int count = 0;
        int total = 0;
        for (int i = 0; i < PREFER_WIDTH * PERFER_HEIGH; i = i + 100) {
            total += byteToInt(tmp[i]);
            count++;
        }

        if (index == 0) {
            camemra1DataMean = total / count;
        } else {
            camemra2DataMean = total / count;
        }
        if (camemra1DataMean != 0 && camemra2DataMean != 0) {
            if (camemra1DataMean > camemra2DataMean) {
                camemra1IsRgb = true;
            } else {
                camemra1IsRgb = false;
            }
            rgbOrIrConfirm = true;
        }
    }

    public int byteToInt(byte b) {
        // Java 总是把 byte 当做有符处理；我们可以通过将其和 0xFF 进行二进制与得到它的无符值
        return b & 0xFF;
    }

    private void choiceRgbOrIrType(int index, byte[] data) {
        // camera1如果为rgb数据，调用dealRgb，否则为Ir数据，调用Ir
        if (index == 0) {
            if (camemra1IsRgb) {
                dealRgb(data);
            } else {
                choiceRgbOrIrType(1, data);
            }
        } else {
            if (camemra1IsRgb) {
                dealIr(data);
            } else {
                choiceRgbOrIrType(0, data);
            }
        }
    }


    private void dealRgb(byte[] data) {
        if (rgbData == null) {

            //if (previewAngle == 90) {
            //    trackAngle = 270;
            //    mTrackImageWidth = PERFER_HEIGH;
            //    mTrackImageHeight = PREFER_WIDTH;
            //} else if (previewAngle == 270) {
            //    trackAngle = 90;
            //    mTrackImageWidth = PERFER_HEIGH;
            //    mTrackImageHeight = PREFER_WIDTH;
            //} else {
            //    trackAngle = previewAngle;
            //    mTrackImageWidth = PREFER_WIDTH;
            //    mTrackImageHeight = PERFER_HEIGH;
            //}
            // 如果预览角度是90、270度（竖屏显示），则需要镜像
            int mirror;
            if (previewAngle == 90 || previewAngle == 270) {
                mirror = 1;
            } else { // 如果预览角度是0、180度（横屏显示），则不需要镜像
                mirror = 0;
            }

            final int[] argb = new int[PREFER_WIDTH * PERFER_HEIGH];
            FaceSDKManager.getInstance().getFaceDetector().yuvToARGB(data, PREFER_WIDTH, PERFER_HEIGH, argb, trackAngle, mirror);
            rgbData = argb;

            // 用于显示送检时的图片，建议注释掉
            post(new Runnable() {
                @Override
                public void run() {
                    if (mImageTracker == null) {
                        return;
                    }
                    mImageTracker.setImageBitmap(Utils.getBitmap(argb, mTrackImageHeight, mTrackImageWidth));
                }
            });
        }
        checkData();
    }

    private void dealIr(byte[] data) {
        if (irData == null) {
            niRargb = new int[PREFER_WIDTH * PERFER_HEIGH];

            int mirror;
            if (previewAngle == 90 || previewAngle == 270) {
                mirror = 1;
            } else {
                mirror = 0;
            }
            //previewAngle camera1 预览角度
            //trackAngle camera1 检测角度
            int irTrackAngle;
            irTrackAngle = trackAngle;
            FaceSDKManager.getInstance().getFaceDetector().yuvToARGB(data, PREFER_WIDTH, PERFER_HEIGH, niRargb, irTrackAngle, mirror);
            final byte[] ir = new byte[PREFER_WIDTH * PERFER_HEIGH];
            System.arraycopy(data, 0, ir, 0, PREFER_WIDTH * PERFER_HEIGH);
            irData = ir;

            // 用于显示送检时的图片，建议注释掉
            post(new Runnable() {
                @Override
                public void run() {
                    if (mImageTrackerIr == null) {
                        return;
                    }
                    mImageTrackerIr.setImageBitmap(Utils.getBitmap(niRargb, mTrackImageHeight, mTrackImageWidth));
                }
            });

        }
        checkData();
    }

    private synchronized void checkData() {
        if (rgbData != null && irData != null) {
            FaceSDKManager.getInstance().getFaceLiveness().setNirRgbInt(niRargb);
            FaceSDKManager.getInstance().getFaceLiveness().setRgbInt(rgbData);
            FaceSDKManager.getInstance().getFaceLiveness().setIrData(irData);
            FaceSDKManager.getInstance().getFaceLiveness().livenessCheck(mTrackImageWidth, mTrackImageHeight, 0x0011);
            rgbData = null;
            irData = null;
        }
    }

    @Override
    public void onTip(int code, String msg) {

    }

    @Override
    public void onCanvasRectCallback(LivenessModel livenessModel) {

    }

    @Override
    public void onCallback(int code, LivenessModel livenessModel) {
        if (livenessCallBack != null) {
            livenessCallBack.onCallback(code, livenessModel);
        }
    }
}
