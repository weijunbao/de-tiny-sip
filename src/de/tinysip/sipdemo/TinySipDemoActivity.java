/*
 * This file is part of TinySip. 
 * http://code.google.com/p/de-tiny-sip/
 * 
 * Created 2011 by Sebastian Rösch <flowfire@sebastianroesch.de>
 * 
 * This software is licensed under the Apache License 2.0.
 */

package de.tinysip.sipdemo;

import java.util.ArrayList;

import javax.sdp.SdpConstants;

import de.javawi.jstun.test.DiscoveryInfo;
import de.tinysip.sip.LocalSipProfile;
import de.tinysip.sip.SipAudioFormat;
import de.tinysip.sip.SipContact;
import de.tinysip.sip.SipManager;
import de.tinysip.sip.SipManagerCallStatusEvent;
import de.tinysip.sip.SipManagerSessionEvent;
import de.tinysip.sip.SipManagerState;
import de.tinysip.sip.SipManagerStatusChangedEvent;
import de.tinysip.sip.SipManagerStatusListener;
import de.tinysip.sip.SipSession;
import de.tinysip.stun.STUNDiscoveryResultEvent;
import de.tinysip.stun.STUNDiscoveryResultListener;
import de.tinysip.stun.STUNDiscoveryTask;
import de.tinysip.stun.STUNInfo;
import de.tinysip.sipdemo.R;
import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

public class TinySipDemoActivity extends Activity implements STUNDiscoveryResultListener, SipManagerStatusListener{
    private static String TAG = "tSIP";
	private TextView sipStatusText;
	
	private LocalSipProfile localSipProfile;
	private SipContact sipContact;
    private STUNDiscoveryTask sipPortTask;
	private DiscoveryInfo sipDiscoveryInfo;
	private SipManager sipManager;
	
	private SipManagerState state;
	
	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
		sipStatusText = (TextView) findViewById(R.id.sipstatustext);
        
		// the local user's credentials (change details in SettingsProvider)
        localSipProfile = new LocalSipProfile(SettingsProvider.sipUserName, SettingsProvider.sipDomain, SettingsProvider.sipPassword, SettingsProvider.sipPort, SettingsProvider.displayName);
        
        // create a list of supported audio formats for the local user agent
        ArrayList<SipAudioFormat> audioFormats = new ArrayList<SipAudioFormat>();
        audioFormats.add(new SipAudioFormat(SdpConstants.PCMA, "PCMA", 8000));
        localSipProfile.setAudioFormats(audioFormats);
        
        // set ports for rtp and rtcp
        localSipProfile.setLocalRtpPort(5071);
        localSipProfile.setLocalRtcpPort(5072);
        
        // the sip contact to call (change details in SettingsProvider)
        sipContact = new SipContact(SettingsProvider.callContact, SettingsProvider.callDomain, true);

        // the STUN server and port for NAT traversal
		STUNInfo sipPortInfo = new STUNInfo(STUNInfo.TYPE_SIP, "stun.sipgate.net", 10000);
		sipPortInfo.setLocalPort(5070); // local port to use for SIP
		sipPortTask = new STUNDiscoveryTask();
		sipPortTask.addResultListener(this);
		sipPortTask.execute(sipPortInfo);
		sipStatusText.append("\n" + getString(R.string.STUNDiscovery));
    }

	@Override
	public void SipManagerStatusChanged(SipManagerStatusChangedEvent event) {
		final SipManagerStatusChangedEvent fevent = event;
		this.runOnUiThread(new Runnable()
		{
			@Override
			public void run()
			{
				state = fevent.getState();

				switch (state)
				{
				case IDLE:
					break;
				case RINGING:
					break;
				case ESTABLISHED:
					break;
				case INCOMING:
					break;
				case ERROR:
					break;
				case UNREGISTERING:
					sipStatusText.append("\n" + getString(R.string.SIPUnregistering));
					break;
				case TIMEOUT:
					break;
				case READY:
					sipStatusText.append("\n" + getString(R.string.SIPReady));
					sipStatusText.append("\n" + getString(R.string.SIPInvite) + " " + sipContact.toString());
					try {
						sipManager.sendInvite(sipContact);
					} catch (Exception e) {
						sipStatusText.append("\n" + getString(R.string.SIPRegistering));
					}
					break;
				case REGISTERING:
					sipStatusText.append("\n" + getString(R.string.SIPRegistering));
					break;
				default:
					break;
				}
			}
		});		
	}

	@Override
	public void SipManagerCallStatusChanged(SipManagerCallStatusEvent event) {
		final SipManagerCallStatusEvent fevent = event;
		this.runOnUiThread(new Runnable()
		{
			@Override
			public void run()
			{
				sipStatusText.append("\n" + fevent.getMessage());
			}
		});		
	}

	@Override
	public void SipManagerSessionChanged(SipManagerSessionEvent event) {
		final SipManagerSessionEvent fevent = event;
	this.runOnUiThread(new Runnable()
	{
		@Override
		public void run()
		{
			SipSession session = fevent.getSession();

			if (session != null)
			{
				sipStatusText.append("\n" + getString(R.string.SIPCallConnected) + " " + session.getCallerNumber());
			}
		}
	});
	}

	@Override
	public void STUNDiscoveryResultChanged(STUNDiscoveryResultEvent event) {
		if (event.getDiscoveryInfo() != null)
		{
			Log.d(TAG, event.getDiscoveryInfo().toString());

			DiscoveryInfo discoveryInfo = event.getDiscoveryInfo();
			STUNInfo stunInfo = event.getStunInfo();

			switch (stunInfo.getType())
			{
			case STUNInfo.TYPE_SIP:
				sipDiscoveryInfo = discoveryInfo;
				// STUN test was completed, start SIP registration now
				startSipRegistration();
				break;
			}
		}
		else
		{
			sipStatusText.append("\n" + getString(R.string.STUNError));
		}		
	}
	
	private void startSipRegistration()
	{
		sipManager = SipManager.createInstance(localSipProfile, sipDiscoveryInfo);
		sipManager.addStatusListener(this);

		try
		{
			sipManager.registerProfile();
		}
		catch (Exception e)
		{
			sipStatusText.append("\n" + getString(R.string.SIPRegistrationError));
		}
	}
}