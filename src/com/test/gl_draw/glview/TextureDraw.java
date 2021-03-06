
package com.test.gl_draw.glview;

import java.nio.FloatBuffer;

import javax.microedition.khronos.opengles.GL10;

import android.graphics.Color;
import android.graphics.RectF;
import android.opengl.GLES20;
import android.opengl.Matrix;

import com.test.gl_draw.data.Texture;
import com.test.gl_draw.gl_base.GLConfigure;
import com.test.gl_draw.gl_base.GLObject;
import com.test.gl_draw.gl_base.GLRender;
import com.test.gl_draw.gl_base.GLShadeManager;
import com.test.gl_draw.utils.helper.BufferUtil;

public class TextureDraw extends GLObject {

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
    
    private float mAlpha = -1;

    private RectF mTextureVisibleRectF = new RectF();


    private boolean mRecyleBitmapWhenDetach = true;
    
    public TextureDraw() {
    	SetAlpha(1);
    }
    
    public void SetColor(int... color) {
        if (color.length != 1 && color.length != 2 && color.length != 4)
            throw new RuntimeException("背景颜色个数设置错误！");

        if (mColor != color) {
            mColor = color;
            refreshColor();
        }
    }

    public void SetAlpha(float alpha) {
        alpha = Math.max(0, Math.min(1, alpha));
        
        if (mAlpha == alpha && mColorBuffer != null)
            return;
        
        mAlpha = alpha;
        
        if (mAlpha == 0 && mTexture != null)
            mTexture.Destory(false);
        
        int alpha_i = (int) (255 * mAlpha + 0.5);

        if (mColor == null) {
            mColor = new int[] {
                    Color.argb(alpha_i, 255, 255, 255)
            };
        } else {
            for (int i = 0; i < mColor.length; i++) {
                int r = Color.red(mColor[i]);
                int b = Color.blue(mColor[i]);
                int g = Color.green(mColor[i]);
                mColor[i] = Color.argb(alpha_i, r, g, b);
            }
        }

        refreshColor();
    }

    public void SetTexture(Texture texture, boolean recyle_bitmap_when_detach) {
        SetTexture(texture, null, recyle_bitmap_when_detach);

    }

    public void SetTexture(Texture texture, RectF visible_rect, boolean recyle_bitmap_when_detach) {
        if (texture == null || texture == mTexture)
            return;

        mRecyleBitmapWhenDetach = recyle_bitmap_when_detach || !GLConfigure.getInstance().isSupportNPOT();
        
        if (visible_rect == null || visible_rect.isEmpty()) {
            int[] size = texture.getTextSize();

            mTextureVisibleRectF.set(0, 0, size[0], size[1]);
        }

        if (mTexture != null) {
            mTexture.Destory(recyle_bitmap_when_detach);
        }

        mTexture = texture;
        
        //
        int[] size = mTexture.getTextSize();
        int[] real_size = mTexture.getRealSize();
        
        float delt_x = (real_size[0] - size[0])/2.0f;
        float delt_y = (real_size[1] - size[1])/2.0f;
        
        mTextureVisibleRectF.offset(delt_x, delt_y);
        
        mTextureVisibleRectF.set(
                mTextureVisibleRectF.left/real_size[0],
                mTextureVisibleRectF.top/real_size[1],
                mTextureVisibleRectF.right/real_size[0],
                mTextureVisibleRectF.bottom/real_size[1]);
        
        refreshTXData();
    }
    
    public void UnloadTexture() {
        if (mTexture != null) {
            mTexture.Destory(false);
        }
    }
    
    public void DetachFromView() {
    	if (mTexture != null) {
    		mTexture.Destory(mRecyleBitmapWhenDetach);
    		mTexture = null;
    	}
    	
    	if (mVBuffer != null) {
    		mVBuffer.clear();
    		mVBuffer = null;
    	}
    	
    	if (mTXCoordBuffer != null) {
    		mTXCoordBuffer.clear();
    		mTXCoordBuffer = null;
    	}
    	
    	if (mColorBuffer != null) {
    		mColorBuffer.clear();
    		mColorBuffer = null;
    	}
    	
    	mRenderRect.setEmpty();
    }

    public void SetRenderRect(RectF rc) {
        if (rc == null || rc.equals(mRenderRect))
            return;

        mRenderRect.set(rc);
        refreshVData();
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

        refreshVData();
    }

    public void setVisible(boolean visible) {
        if (mVisible == visible)
            return;
        
        mVisible = visible;
        if (mTexture == null)
            return;
        
        if (!mVisible) {
            mTexture.Destory(false);
        } else {
            mTexture.ReloadIfNeed(GLRender.GL());
        }
    }

    public Texture getTexture() {
        return mTexture;
    }
    
    public int[] getTextSize() {
        if (mTexture == null)
            return new int[2];
        else {
            return mTexture.getTextSize();
        }
    }

    public void Draw(GL10 gl) {
      
        if (mRenderRect.isEmpty() || !mVisible || mAlpha == 0)
            return;
        
        boolean has_texture = mTexture != null && mTexture.ReloadIfNeed(gl);

        boolean has_color = mColorBuffer != null;

        if (!has_texture && !has_color)
            return;
 
        BeforeThreadCall();
        
        GLShadeManager shade_mgr = GLShadeManager.getInstance();

        shade_mgr.SetHasTexture(has_texture);
        
        if (has_texture) {
            mTexture.bind(gl);
            GLES20.glUniform1i(shade_mgr.getTextureUniformHandle(), 0);
        	GLES20.glEnableVertexAttribArray(shade_mgr.getTexCoordHandle());
        	
        	GLES20.glVertexAttribPointer(shade_mgr.getTexCoordHandle(), 2,
    				GLES20.GL_FLOAT, false, 0, mTXCoordBuffer);
    
        } else {
        	GLES20.glDisableVertexAttribArray(shade_mgr.getTexCoordHandle());
        }

        if (has_color) {
        	GLES20.glEnableVertexAttribArray(shade_mgr.getColorHandle());
        	GLES20.glVertexAttribPointer(shade_mgr.getColorHandle(), 4,
    				GLES20.GL_FLOAT, false, 0, mColorBuffer);
        } else {
        	GLES20.glDisableVertexAttribArray(shade_mgr.getColorHandle());
        }

        GLES20.glEnableVertexAttribArray(shade_mgr.getVertexHandle());
		GLES20.glVertexAttribPointer(shade_mgr.getVertexHandle(), 2,
				GLES20.GL_FLOAT, false, 0, mVBuffer);
		
		// This multiplies the view matrix by the model matrix, and stores the
		// result in the MVP matrix
		// (which currently contains model * view).
		Matrix.multiplyMM(shade_mgr.getMVPMatrix(), 0,
				shade_mgr.getViewMatrix(), 0, shade_mgr.getModelMatrix(), 0);

		// Pass in the modelview matrix.
		GLES20.glUniformMatrix4fv(shade_mgr.getMVMatrixHandle(), 1, false,
				shade_mgr.getMVPMatrix(), 0);

		// This multiplies the modelview matrix by the projection matrix, and
		// stores the result in the MVP matrix
		// (which now contains model * view * projection).
		Matrix.multiplyMM(shade_mgr.getMVPMatrix(), 0,
				shade_mgr.getProjectionMatrix(), 0, shade_mgr.getMVPMatrix(),
				0);

		// Pass in the combined matrix.
		GLES20.glUniformMatrix4fv(shade_mgr.getMVPMatrixHandle(), 1, false,
				shade_mgr.getMVPMatrix(), 0);
		
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        //
        if (has_texture) {
            mTexture.unBind(gl);
        }

        AfterThreadCall();
    }

    //
    private void refreshVData() {

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
        if (mTexture == null)
            return;

        RectF t_r = new RectF(mTexture.getTextRect());

        if (!t_r.intersect(mTextureVisibleRectF))
            t_r.setEmpty();

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
