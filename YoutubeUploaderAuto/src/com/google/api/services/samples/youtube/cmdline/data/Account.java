package com.google.api.services.samples.youtube.cmdline.data;

public class Account {
	public String username;
	public String password;
	public String developerKey;
	public int delay;
	public int videoUploadNumber;
	public boolean ignored = false; // because invalid login or videoUploadNumber is over videonumber
}
