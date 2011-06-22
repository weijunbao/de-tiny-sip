/*
 * This file is part of TinySip. 
 * http://code.google.com/p/de-tiny-sip/
 * 
 * Created 2011 by Sebastian Rösch <flowfire@sebastianroesch.de>
 * 
 * This software is licensed under the Apache License 2.0.
 */

package de.tinysip.sip;

/**
 * Contains the information about a supported audio format.
 * 
 * @author Sebastian
 * 
 */
public class SipAudioFormat {
	private int format;
	private String formatName;
	private int sampleRate;

	/**
	 * Create a SipAudioFormat which is supported by the application. This is needed for the sip session. Specify the integer format code, the format name and the sample rate.
	 * 
	 * @param format an integer value specifying the audio format. Use SDPConstants for values
	 * @param formatName a string representation for the audio format
	 * @param sampleRate the supported sample rate of the audio format
	 */
	public SipAudioFormat(int format, String formatName, int sampleRate) {
		this.format = format;
		this.formatName = formatName;
		this.sampleRate = sampleRate;
	}

	/**
	 * @return the format code
	 */
	public int getFormat() {
		return format;
	}

	/**
	 * @return the format name
	 */
	public String getFormatName() {
		return formatName;
	}

	/**
	 * @return the format sample rate
	 */
	public int getSampleRate() {
		return sampleRate;
	}

	/**
	 * @return concatenated format data used by the sdp header. Example: "8 PCMA/8000"
	 */
	public String getSdpField() {
		return format + " " + formatName + "/" + sampleRate;
	}

	/**
	 * Parse a SipAudioFormat from an audio format string.
	 * 
	 * @param audioFormat the string to parse
	 * @return the parsed SipAudioFormat
	 */
	public static SipAudioFormat parseAudioFormat(String audioFormat) {
		try {
			String trim = audioFormat.replace("rtpmap:", "");
			String format = trim.split(" ")[0];
			String formatName = trim.split(" ")[1].split("/")[0];
			String sampleRate = trim.split(" ")[1].split("/")[1];

			return new SipAudioFormat(Integer.parseInt(format), formatName, Integer.parseInt(sampleRate));
		} catch (Exception e) {
			return null;
		}
	}

}
