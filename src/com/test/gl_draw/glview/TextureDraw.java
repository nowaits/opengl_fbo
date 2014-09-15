
package com.test.gl_draw.glview;

import java.nio.FloatBuffer;

import javax.microedition.khronos.opengles.GL10;

import android.graphics.Color;
import android.graphics.RectF;

import com.test.gl_draw.gl_base.Texture;
import com.test.gl_draw.utils.BufferUtil;
import com.test.gl_draw.utils.GLHelper;

public class TextureDraw {

    public enum FillMode {
        FitXY, // 根据绘制区域拉申
        ScaleXY, // 等比例拉申
        ScaleNeed, // 绘制空间不够时，才等比例拉申
    }

    private RectF mRenderRect = new RectF();
    protected int[] mColor;
    private Texture mTexture = null;

    private FillMode mFillMode = FillMode.ScaleXY;

    private FloatBuffer mVBuffer;
    private FloatBuffer mTXCoordBuffer;
    private FloatBuffer mColorBuffer;

    private boolean mVisible = true;

    public void SetColor(int... color) {
        if (color.length != 1 && color.length != 2 && color.length != 4)
            throw new RuntimeException("背景颜色个数设置错误！");

        if (mColor != color) {
            mColor = color;
            refreshColor();
        }
    }

    public void SetTexture(Texture texture) {
        if (texture == null || !texture.isValid())
            return;

        if (mTexture == null)
            mTexture = new Texture();

        mTexture.Init(texture);
        refreshTXData();
    }

    public void SetRenderRect(RectF rc) {
        if (rc == null || rc.equals(mRenderRect))
            return;

        mRenderRect.set(rc);
        refreshTextureData();
    }

    public void SetRenderRect(float... xywh) {
        if (xywh.length < 4)
            return;

        RectF rc = new RectF(xywh[0], xywh[1], xywh[2] + xywh[0], xywh[3] + xywh[1]);
        SetRenderRect(rc);
    }

    public void SetFillMode(FillMode mode) {
        if (mFillMode == mode)
            return;

        mFillMode = mode;

        refreshTextureData();
    }

    public void setVisible(boolean visible) {
        mVisible = visible;
    }

    public Texture getTexture() {
        return mTexture;
    }

    public void Draw(GL10 gl) {

        if (mRenderRect.isEmpty() || !mVisible)
            return;

        boolean has_texture = mTexture != null && mTexture.isValid();

        boolean has_color = mColorBuffer != null;

        if (!has_texture && !has_color)
            return;

        if (has_texture) {
            gl.glBindTexture(GL10.GL_TEXTURE_2D, mTexture.getTexture());
            gl.glTexCoordPointer(2, GL10.GL_FLOAT, 0, mTXCoordBuffer);
        } else {
            gl.glDisableClientState(GL10.GL_TEXTURE_COORD_ARRAY);
        }

        if (has_color) {
            gl.glColorPointer(4, GL10.GL_FLOAT, 0, mColorBuffer);
        } else {
            gl.glDisableClientState(GL10.GL_COLOR_ARRAY);
        }

        gl.glVertexPointer(2, GL10.GL_FLOAT, 0, mVBuffer);

        gl.glDrawArrays(GL10.GL_TRIANGLE_STRIP, 0, 4);

        //
        if (has_texture) {
            gl.glBindTexture(GL10.GL_TEXTURE_2D, 0);

        } else {
            gl.glEnableClientState(GL10.GL_TEXTURE_COORD_ARRAY);
        }

        if (!has_color) {
            gl.glEnableClientState(GL10.GL_COLOR_ARRAY);
        }

        GLHelper.checkGLError();
    }

    //
    private void refreshTextureData() {

        if (mRenderRect.isEmpty())
            return;

        RectF rc = new RectF(mRenderRect);

        if (mTexture != null && mFillMode != FillMode.FitXY) {
            int[] size = mTexture.getTextSize();
            float w = size[0];
            float h = size[1];
            // 1:1居中对齐
            if (mFillMode == FillMode.ScaleXY || rc.width() < size[0]
                    || rc.height() < size[1]) {

                if (rc.width() / rc.height() < size[0] / (float) size[1]) {
                    w = rc.width();
                    h = size[1] * w / size[0];
                } else {
                    h = rc.height();
                    w = size[0] * h / size[1];
                }
            }

            rc.set(rc.left + (rc.width() - w) / 2, rc.top + (rc.height() - h)
                    / 2, rc.left + (rc.width() + w) / 2, rc.top
                    + (rc.height() + h) / 2);
        }

        float[] pos = {
                //
                rc.left, rc.top,//
                rc.right, rc.top, //
                rc.left, rc.bottom,//
                rc.right, rc.bottom,
        };

        mVBuffer = BufferUtil.newFloatBuffer(pos.length);
        mVBuffer.put(pos);
        mVBuffer.position(0);

    }

    private void refreshTXData() {
        if (mTexture == null || !mTexture.isValid())
            return;

        RectF t_r = mTexture.getTextRect();
        float[] f = {
                //
                t_r.left, t_r.top,//
                t_r.right, t_r.top, //
                t_r.left, t_r.bottom,//
                t_r.right, t_r.bottom,
        };

        mTXCoordBuffer = BufferUtil.newFloatBuffer(f.length);
        mTXCoordBuffer.put(f);
        mTXCoordBuffer.position(0);
    }

    private void refreshColor() {

        float rgba[][] = null;
        if (mColor.length == 1) {
            rgba = new float[][] {
                {
                        Color.red(mColor[0]) / 255.0f,
                        Color.green(mColor[0]) / 255.0f,
                        Color.blue(mColor[0]) / 255.0f,
                        Color.alpha(mColor[0]) / 255.0f,
                }
            };

        } else if (mColor.length == 2) {
            rgba = new float[][] {
                    {
                            Color.red(mColor[0]) / 255.0f,
                            Color.green(mColor[0]) / 255.0f,
                            Color.blue(mColor[0]) / 255.0f,
                            Color.alpha(mColor[0]) / 255.0f,
                    },
                    {
                            Color.red(mColor[1]) / 255.0f,
                            Color.green(mColor[1]) / 255.0f,
                            Color.blue(mColor[1]) / 255.0f,
                            Color.alpha(mColor[1]) / 255.0f,
                    },
            };

        } else if (mColor.length == 4) {
            rgba = new float[][] {
                    {
                            Color.red(mColor[0]) / 255.0f,
                            Color.green(mColor[0]) / 255.0f,
                            Color.blue(mColor[0]) / 255.0f,
                            Color.alpha(mColor[0]) / 255.0f,
                    },
                    {
                            Color.red(mColor[1]) / 255.0f,
                            Color.green(mColor[1]) / 255.0f,
                            Color.blue(mColor[1]) / 255.0f,
                            Color.alpha(mColor[1]) / 255.0f,
                    },
                    {
                            Color.red(mColor[2]) / 255.0f,
                            Color.green(mColor[2]) / 255.0f,
                            Color.blue(mColor[2]) / 255.0f,
                            Color.alpha(mColor[2]) / 255.0f,
                    },
                    {
                            Color.red(mColor[3]) / 255.0f,
                            Color.green(mColor[3]) / 255.0f,
                            Color.blue(mColor[3]) / 255.0f,
                            Color.alpha(mColor[3]) / 255.0f,
                    },
            };
        }

        if (rgba == null)
            return;

        mColorBuffer = BufferUtil.newFloatBuffer(4 * 4);
        for (int i = 0; i < rgba.length; i++) {
            for (int j = 0; j < 4 / rgba.length; j++) {
                int p = i * 4 / rgba.length + j;
                for (int k = 0; k < rgba[i].length; k++) {
                    mColorBuffer.put(p * 4 + k, rgba[i][k]);
                }
            }
        }
    }
}