package com.lgcns.sce.nfc;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;

import com.lgcns.sce.nfc.R;

import android.app.Activity;
import android.content.Intent;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcAdapter.CreateNdefMessageCallback;
import android.nfc.NfcAdapter.OnNdefPushCompleteCallback;
import android.nfc.NfcEvent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.telephony.SmsManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class BeamActivity extends Activity implements
		CreateNdefMessageCallback, OnNdefPushCompleteCallback {

	// NFC adapter
	private static final String MIME_TYPE = "application/com.lgcns.sce.nfc";
	private static final String PACKAGE_NAME = "com.lgcns.sce.nfc";
	private static final int MESSAGE_SENT = 1;
	NfcAdapter mNfcAdapter;

	// socket통신으로 PC에 메시지보낼 때의 송/수신 상황 구분
	public static final int FROM_PC_CLIENT = 1;
	private static final int TO_PC_CLIENT = 2;
	private SocketServer server;
	private Thread serverThread;

	// Text 임시저장용
	private TextView centerTextView;
//	private Button mSendButton;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_beam);

		// Check for available NFC Adapter
		mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
		if (mNfcAdapter == null) {
			Toast.makeText(this, "Sorry, NFC is not available on this xdevice",
					Toast.LENGTH_SHORT).show();
		} else {
			// Register callback to set NDEF message
			mNfcAdapter.setNdefPushMessageCallback(this, this);
			// Register callback to listen for message-sent success
			mNfcAdapter.setOnNdefPushCompleteCallback(this, this);
		}

		// PC에서 수신된 메시지를 처리할 핸들러
		SocketHandler handler = new SocketHandler();

		// 백그라운드 스레드에서 Socket Server 구동
		server = new SocketServer(handler);
		serverThread = new Thread(server);
		serverThread.start();

		centerTextView = (TextView) findViewById(R.id.center_text);

		// PC와의 socket통신 테스트용 소스
//		mSendButton = (Button) findViewById(R.id.btn_send);
//		mSendButton.setOnClickListener(new OnClickListener() {
//			@Override
//			public void onClick(View v) {
//				Message temp_msg = Message.obtain();
//				temp_msg.what = TO_PC_CLIENT;
//				server.mMessageHandler.sendMessage("01044447777");
//			}
//		});
	}

	/**
	 * Implementation for the CreateNdefMessageCallback interface
	 */
	@Override
	public NdefMessage createNdefMessage(NfcEvent event) {
		String text = "NdefMessage전송테스트";
		NdefMessage msg = new NdefMessage(new NdefRecord[] {
				NfcUtils.createRecord(MIME_TYPE, text.getBytes()),
				NdefRecord.createApplicationRecord(PACKAGE_NAME) });
		return msg;
	}

	/** This handler receives a message from onNdefPushComplete */
	private final Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MESSAGE_SENT:
				Toast.makeText(getApplicationContext(), "Message sent!",
						Toast.LENGTH_LONG).show();
				break;
			}
		}
	};

	/**
	 * Implementation for the OnNdefPushCompleteCallback interface
	 */
	@Override
	public void onNdefPushComplete(NfcEvent arg0) {
		// A handler is needed to send messages to the activity when this
		// callback occurs, because it happens from a binder thread
		mHandler.obtainMessage(MESSAGE_SENT).sendToTarget();
	}

	@Override
	public void onNewIntent(Intent intent) {
		// onResume gets called after this to handle the intent
		setIntent(intent);
	}

	@Override
	public void onResume() {
		super.onResume();
		// Check to see that the Activity started due to an Android Beam
		if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(getIntent().getAction())) {
			processIntent(getIntent());
		}
	}

	/**
	 * Parses the NDEF Message from the intent and toast to the user
	 */
	void processIntent(Intent intent) {
		Parcelable[] rawMsgs = intent
				.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
		// in this context, only one message was sent over beam
		NdefMessage msg = (NdefMessage) rawMsgs[0];
		// record 0 contains the MIME type, record 1 is the AAR, if present
		String payload = new String(msg.getRecords()[0].getPayload());
		// Toast.makeText(getApplicationContext(),
		// "Message received over beam: " + payload, Toast.LENGTH_LONG).show();
		Toast.makeText(getApplicationContext(),
				"저희 매장에 찾아주셔서 감사합니다. " + payload + "로 대기번호를 보내드리겠습니다.",
				Toast.LENGTH_LONG).show();

		// pc로 전송
		Message temp_msg = Message.obtain();
		temp_msg.what = TO_PC_CLIENT;
		temp_msg.obj = payload;
		server.mMessageHandler.sendMessage(temp_msg);
	}

	// PC에서 보내온 메시지를 TextView에 표시
	private class SocketHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			switch (msg.what) {
			case FROM_PC_CLIENT:
				String message = (String) msg.obj;
				Toast.makeText(getApplicationContext(),
						"SMS를 전송합니다 내용: " + message, Toast.LENGTH_LONG).show();
				sendSMS(message);
				break;
			default:
				break;
			}
		}
	}

	// 안드로이드용 소켓 서버
	private class SocketServer implements Runnable {

		// 공개 포트
		private static final int SERVER_PORT = 9500;

		// 입출력 스트림 정의
		private ServerSocket mSocketServer = null;
		BufferedWriter mServerWriter = null;
		BufferedReader mReaderFromClient = null;

		// 읽은 메시지를 UI에 전달할 핸들러
		Handler mMainHandler;

		// PC로 메시지 전송을 담당하는 핸들러
		Handler mMessageHandler = new Handler() {
			public void handleMessage(Message msg) {
				switch (msg.what) {
				case TO_PC_CLIENT:
					try {
						mServerWriter.write(msg.obj + "\n");
						mServerWriter.flush();
					} catch (IOException e) {
						e.printStackTrace();
						Toast.makeText(getApplicationContext(),
								e.getCause().getLocalizedMessage(),
								Toast.LENGTH_LONG).show();
					}
					break;

				default:
					break;
				}
			};
		};

		// 소켓 서버 객체 생성자
		public SocketServer(Handler handler) {
			this.mMainHandler = handler;
		}

		@Override
		public void run() {

			try {
				// 소켓 통신 서비스 시작
				mSocketServer = new ServerSocket(SERVER_PORT);
				System.out.println("connecting...");
				Socket client = mSocketServer.accept();

				// 입 출력용 스트림 준비
				InputStream is = client.getInputStream();
				OutputStream os = client.getOutputStream();

				// 입 출력용 스트림 연결
				mReaderFromClient = new BufferedReader(
						new InputStreamReader(is));
				mServerWriter = new BufferedWriter(new OutputStreamWriter(os));
			} catch (IOException e) {
				e.printStackTrace();
				Toast.makeText(getApplicationContext(),
						e.getCause().getLocalizedMessage(), Toast.LENGTH_LONG)
						.show();
			}

			try {
				// PC로부터 들어오는 메시지 수신부
				while (true) {
					String msg = "";
					msg = mReaderFromClient.readLine();

					if (msg.equals("exit")) {
						break;
					} else if (msg != null && msg != "") {
						Message message = Message.obtain(mMainHandler,
								FROM_PC_CLIENT);
						message.obj = msg;
						sendSMS((String) message.obj);
						// mMainHandler.sendMessage(message);
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
				Toast.makeText(getApplicationContext(),
						e.getCause().getLocalizedMessage(), Toast.LENGTH_LONG)
						.show();
			}
			try {
				// 소켓 종료
				mServerWriter.close();
				mReaderFromClient.close();
				mSocketServer.close();
			} catch (Exception e) {
				Toast.makeText(getApplicationContext(),
						e.getCause().getLocalizedMessage(), Toast.LENGTH_LONG)
						.show();
			}
		}
	}

	void sendSMS(String msg) {
		String phoneNumber;
		String data;
		if(msg.contains("*")){
			phoneNumber = msg.substring(0,msg.indexOf("*"));
			data = msg.substring(msg.indexOf("*")+1,msg.length());
		}else{
			phoneNumber = msg;
			data = "ok";
		}
		try {
			final SmsManager sms = SmsManager.getDefault();
			sms.sendTextMessage(phoneNumber, null, data, null, null);
		} catch (Exception e) {
			Toast.makeText(getApplicationContext(),
					"SMS faild, please try again later!", Toast.LENGTH_LONG)
					.show();
			e.printStackTrace();
		}
	}
}
