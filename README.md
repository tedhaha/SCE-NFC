SCE-NFC
=======

Guest(AndroidBeam)(01044445555) -> device(Socket)(01044445555) -> (Socket)(01044445555*0120*3) -> device(SMS)(0120*3) -> Guest phone

PC(Socket)(01044445555*0120*3) -> device(SMS)(ok) -> Guest phone

1. 에뮬리이터or폰에서 com.lgcns.sce.nfc.BeamAcitivity 실행
2. 도스 프롬프트에서 adb forward tcp:9500 tcp:9500
3. pc에서 socketClient실행

* socket만 테스트할거라면 안드로이드 소스의 버튼 부분을 활성화(manifest.xml 및 BeamAcitivity)