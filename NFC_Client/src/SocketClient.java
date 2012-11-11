import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

public class SocketClient {

	// �ȵ���̵� ���� ���� ����
	private static final int SERVER_PORT = 9500;
	private static final String SERVER_IP = "localhost";

	// Ŭ���̾�Ʈ ����
	private Socket mClientSocket = null;
	
	// ��ſ� ��Ʈ�� 
	BufferedWriter mClientWriter = null;
	BufferedReader mMessageToServerReader = null;
	BufferedReader mMessageFromServerReader = null;

	// ���� ���� ���� ����
	volatile Integer number = new Integer(0);
	HashMap<Integer, String> waitingList = new HashMap<Integer, String>();

	/**
	 * @method Client Socket ������
	 * @aim ���� ���� �� �޽��� ���� ó��
	 * */
	public SocketClient(String ip, int port) {
		try {
			
			//���� ����
			mClientSocket = new Socket(ip, port);

			// ��/��¿� ��Ʈ�� �غ�
			InputStream is = mClientSocket.getInputStream();
			OutputStream os = mClientSocket.getOutputStream();

			// �ȵ���̵�κ��� ���ŵ� ������
			String msgToServer = null;

			// ��/��¿� ��Ʈ�� ����
			mClientWriter = new BufferedWriter(new OutputStreamWriter(os));

			// �ȵ���̵�� ���� ���� �޽��� ���źδ� ������ ������� �и�
			new Thread(new MessageReader()).start();

			do {
				mMessageToServerReader = new BufferedReader(new InputStreamReader(System.in));
				mMessageFromServerReader = new BufferedReader(new InputStreamReader(is));
				// �����޴����
				printWaitingList();
				msgToServer = mMessageToServerReader.readLine();
				
				// �ӽ� �ҽ�
				mClientWriter.write(msgToServer);
				mClientWriter.flush();
				
//				// ���� ��Ͽ� �ִ� ��ȣ�� ��� �ȵ���̵忡 ���� ������ ��Ͽ��� ����
//				Integer thisKey = Integer.parseInt(msgToServer);
//				if (waitingList.containsKey(thisKey)) {
//					mClientWriter.write(waitingList.get(thisKey) + "\n");
//					mClientWriter.flush();
//					waitingList.remove(thisKey);
//				}
				mMessageToServerReader.close();
			} while (!(msgToServer.equals("exit")));

		} catch (Exception e) {
			System.out.println("Connection fail");
		}
		try {
			// ����� ���� �ݱ�
			mClientWriter.close();
			mMessageToServerReader.close();
			mClientSocket.close();
		} catch (Exception e) {
		}

	}

	/**
	 * @aim ������ ��� ���
	 * */
	private void printWaitingList() {
		System.out.println("\n\n***************************************************");
		System.out.println("   Waitings...");
		System.out.println("***************************************************");
		Set<Integer> list = waitingList.keySet();
		for (Iterator<Integer> i = list.iterator(); i.hasNext();) {
			Integer key = i.next();
			System.out.println(" * " + key + "  :  " + waitingList.get(key));
		}
		System.out.println("***************************************************");
		System.out.print("Select : ");
	}

	/**
	 * @method ���α׷� ���� �Լ�
	 * */
	public static void main(String[] args) {
		SocketClient ob = new SocketClient(SERVER_IP, SERVER_PORT);
	}

	/**
	 * @aim �ȵ���̵�κ��� ������ �޽��� ó��
	 * */
	private class MessageReader implements Runnable {

		@Override
		public void run() {
			String msgFromServer = null;
			try {
				while (true) {
					msgFromServer = mMessageFromServerReader.readLine();
					if ("REVC_OK".equals(msgFromServer)) {
						System.out.println("\n\n[Device_Message] RECV_OK\n\n");
					} else if (msgFromServer != null) {
						waitingList.put(++number, msgFromServer);
						printWaitingList();
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
			}

		}
	}

}