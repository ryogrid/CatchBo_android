package com.dcatch;

import java.io.IOException;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import com.appiaries.baas.sdk.*;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.view.ViewGroup;
import android.widget.EditText;
import android.text.SpannableStringBuilder;

public class DigitalCatch extends Activity implements View.OnClickListener, SensorEventListener {
	private final int WRAP_CONTENT = ViewGroup.LayoutParams.WRAP_CONTENT;

	private final int SEND = 1;
	private final int RECV = 2;
	private final int NONE = 3;
	private int side = SEND;
	
	private SensorManager manager;	

	private Button button;
	private Button button2;
	private Button button3;

	private EditText edit;
	private TextView tv_top;
	
	private int between;
	private SoundPool mSp;
	private int mIdThrow = -1;
	private int mIdCatc = -1;	
	
	private double last_throw_unix_time = 0;
	
	private Activity parent = null;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		parent = this;

		Context context = getApplicationContext();
		AB.Config.setDatastoreID("_sandbox"); // アピアリーズのデータストアID
		AB.Config.setApplicationID("degital_catchball"); // アピアリーズのアプリID
		AB.Config.setApplicationToken("appda50e66ee8dcd3bf9824fc25bf"); // アピアリーズのアプリトークン
		AB.activate(context);

		LinearLayout linearLayout = new LinearLayout(this);
		linearLayout.setOrientation(LinearLayout.VERTICAL);
		setContentView(linearLayout);

		tv_top = new TextView(this);
		tv_top.setText("番号を入力&距離(メートル)選択して投げてみよう");
		linearLayout.addView(tv_top, new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));

		TextView tv = new TextView(this);
		tv.setText("共通の番号を入れてね");
		linearLayout.addView(tv, new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));

		edit = new EditText(this);
		edit.setWidth(200);
		edit.setText("ここに入力してね");
		linearLayout.addView(edit, new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
	
        ArrayAdapter adapter = new ArrayAdapter(this, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
 
        // アイテムを追加します      
        adapter.add("1");
        adapter.add("3");
        adapter.add("5");
        adapter.add("10");
        adapter.add("30");
        adapter.add("100");
 
        Spinner spinner = new Spinner(this);
        // アダプターを設定します
        spinner.setAdapter(adapter);
        // スピナーのアイテムが選択された時に呼び出されるコールバックリスナーを登録します
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView parent, View view,
                    int position, long id) {
                Spinner spinner = (Spinner) parent;
                // 選択されたアイテムを取得します
                String item = (String) spinner.getSelectedItem();
                between = Integer.parseInt(item);
            }
            @Override
            public void onNothingSelected(AdapterView arg0) {
            }
        });
        linearLayout.addView(spinner, new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));                

		button3 = new Button(this);
		button3.setText("受け側");
		button3.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				side = RECV;
			}
		});
		linearLayout.addView(button3, new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));

		Timer timer = new Timer();
		TimerTask check_task = new CheckTask();
		timer.scheduleAtFixedRate(check_task, 100, 100);
		
		manager = (SensorManager)getSystemService(SENSOR_SERVICE);
		
		 //音量ボタンが音楽のStreamを制御するように紐付ける
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        
        //SoundPoolオブジェクトを取得
        mSp = new SoundPool(10, AudioManager.STREAM_MUSIC, 0);
        
        //AssetManagerを取得
        AssetManager am = getAssets();
        try {
        	//AssetFileDescriptorを取得
			AssetFileDescriptor fd = am.openFd("throw.mp3");
			//FileDescripterを使って、使用するサウンドをloadしIDを取得する
			mIdThrow = mSp.load(fd, 1);
			fd = am.openFd("catc.mp3");
			//FileDescripterを使って、使用するサウンドをloadしIDを取得する	
			mIdCatc = mSp.load(fd, 1);
		} catch (IOException e) {
			e.printStackTrace();
		}
    }		

	public void onClick(View v) {

	}

	@Override
	protected void onResume() {
		super.onResume();
		List<Sensor> sensors = manager.getSensorList(Sensor.TYPE_LINEAR_ACCELERATION);
		if(sensors.size() > 0) {
			Sensor s = sensors.get(0);
			manager.registerListener(this, s, SensorManager.SENSOR_DELAY_UI);
		}
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {	
	}

	boolean over_threth = false;
	double max_acc = 0;
	
	@Override
	public void onSensorChanged(SensorEvent event) {
		if(event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
//			tv_top.setText("x:" + String.valueOf(event.values[0]) + 
//							"\ny:" + String.valueOf(event.values[1]) + 
//								"\nz:" + String.valueOf(event.values[2]));							
			
			double z_acc = event.values[2];						
			if(z_acc > 7 && ((System.currentTimeMillis() / 1000.0) - last_throw_unix_time) > 1){
				over_threth = true;
				if (z_acc > max_acc) {
					max_acc = z_acc;
				}
			}
				
			if(over_threth && z_acc <  0.5){
				double sec = between / (max_acc * 1.5);
				side = SEND;
//				side = RECV;				
				tv_top.setText(String.valueOf(sec).substring(0, 5) + "秒 " + String.valueOf((3.6 * max_acc * 0.8)).substring(0,5) + "km/h");
				
				over_threth = false;
				max_acc = 0;			
				
				
				double time = (System.currentTimeMillis() / 1000.0) + sec;
				last_throw_unix_time = time;
				SpannableStringBuilder sb = (SpannableStringBuilder) edit.getText();
				ABDBObject obj = new ABDBObject("test"); // オブジェクトを作成するJSONデータ・コレクションのID
				obj.put("id", sb.toString());
				obj.put("sec", String.valueOf(time));

				mSp.play(mIdThrow, 1, 1, 0, 0, 1);

				// 作成 (非同期)
				obj.save(new ResultCallback<ABDBObject>() {
					@Override
					public void done(ABResult<ABDBObject> result, ABException e) {
					}
				});
				
				try {
					Thread.sleep((long)(sec * 1000));
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}

				mSp.play(mIdCatc, 1, 1, 0, 0, 1);
//				removeDB(sb.toString());
			}
		}
	}	
	
	class CheckTask extends TimerTask {

		private int counter = 0;
		
		@Override
		public void run() {
			SpannableStringBuilder sb = (SpannableStringBuilder) edit.getText();
			String id_str = sb.toString();

			counter++;
			
			double sec;
			if ((sec = checkDB(id_str)) != 99999 && side == RECV) {
				removeDB(id_str);
//				AlertDialog.Builder alert = new AlertDialog.Builder(parent);
//		        alert.setTitle("title")
//		        .setMessage(String.valueOf(sec))
//		        .setPositiveButton("OK", null)
//		        .show();				
				if(sec < 0) {
					sec = 0;
				}
				try {
					Thread.sleep((long)(sec * 1000));
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
				side = SEND;
				mSp.play(mIdCatc, 1, 1, 0, 0, 1);
				counter = 0;
			}
			
			if(counter > 600){
				side = NONE;
				counter = 0;
			}
		}
	}
	
	private void removeDB(String id_str) {
		ABQuery query = new ABQuery().from("test").where("id").equalsTo(id_str);
		try {
			ABResult<Void> result = AB.DBService.deleteSynchronouslyWithQuery(query);
		} catch (ABException e) {
		}
	}

	private double checkDB(String id_str) {
		ABQuery query = new ABQuery().from("test").where("id").equalsTo(id_str);

		List<ABDBObject> foundArray;
		try {
			ABResult<List<ABDBObject>> result = AB.DBService.findSynchronouslyWithQuery(query);
			foundArray = result.getData();
		} catch (ABException e) {
			return 99999;
		}

		if (foundArray.size() > 0) {
//			System.out.println(foundArray);
			double time = Double.parseDouble((String)((ABDBObject) foundArray.get(foundArray.size() - 1)).get("sec"));
			double ret = time - (double)(System.currentTimeMillis() / 1000.0);
			System.out.println("sec: " + String.valueOf(ret));
			return ret;
		} else {
			return 99999;
		}
	}	
}