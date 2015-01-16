package net.sourceforge.test;

import java.io.ByteArrayOutputStream;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.ViewGroup.LayoutParams;
import android.widget.ImageView;

public class ImageViewActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		ImageView view = new ImageView(this);
		LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
		String path = getIntent().getStringExtra("filepath");
		view.setImageBitmap(getBitmapAndCompress(path));
		setContentView(view, params);
	}

	private Bitmap getBitmapAndCompress(String path) {
		Bitmap newBitmap = null;
		try {
			newBitmap = getSmallBitmap(path);
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			newBitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);
			int options = 100;
			// 如果大于80kb则再次压缩
			while (baos.toByteArray().length / 1024 > 80 && options != 10) {
				// 清空baos
				baos.reset();
				options -= 10;
				// 这里压缩options%，把压缩后的数据存放到baos中
				newBitmap.compress(Bitmap.CompressFormat.JPEG, options, baos);
			}
			baos.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return newBitmap;
	}
	
	private Bitmap getSmallBitmap(String filePath) {
		final BitmapFactory.Options options = new BitmapFactory.Options();
		options.inJustDecodeBounds = true;
		BitmapFactory.decodeFile(filePath, options);
		options.inSampleSize = calculateInSampleSize(options, 480, 800);
		options.inJustDecodeBounds = false;
		return BitmapFactory.decodeFile(filePath, options);
	}
	
	private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
		final int height = options.outHeight;
		final int width = options.outWidth;
		int inSampleSize = 1;

		if (height > reqHeight || width > reqWidth) {
			final int heightRatio = Math.round((float) height / (float) reqHeight);
			final int widthRatio = Math.round((float) width / (float) reqWidth);
			inSampleSize = heightRatio < widthRatio ? heightRatio : widthRatio;
		}
		return inSampleSize;
	}
}
