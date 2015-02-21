/* Copyright (c) 2008 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package sample.youtube;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import sample.util.SimpleCommandLineParser;
import au.com.bytecode.opencsv.CSV;
import au.com.bytecode.opencsv.CSVReadProc;
import au.com.bytecode.opencsv.CSVWriteProc;
import au.com.bytecode.opencsv.CSVWriter;

import com.google.api.client.util.Charsets;
import com.google.api.services.samples.youtube.cmdline.data.Account;
import com.google.api.services.samples.youtube.cmdline.data.Output;
import com.google.common.io.Files;
import com.google.gdata.client.media.ResumableGDataFileUploader;
import com.google.gdata.client.youtube.YouTubeService;
import com.google.gdata.data.media.MediaFileSource;
import com.google.gdata.data.media.mediarss.MediaCategory;
import com.google.gdata.data.media.mediarss.MediaDescription;
import com.google.gdata.data.media.mediarss.MediaKeywords;
import com.google.gdata.data.media.mediarss.MediaTitle;
import com.google.gdata.data.youtube.VideoEntry;
import com.google.gdata.data.youtube.YouTubeMediaGroup;
import com.google.gdata.data.youtube.YouTubeNamespace;
import com.google.gdata.util.AuthenticationException;
import com.google.gdata.util.ServiceException;

/**
 * Demonstrates YouTube Data API operation to upload large media files.
 *
 * 
 */
public class YouTubeUploadClient {

	

	private static final String APP_NAME = "497537842844-jd55qijeifck9smbn03j8bm7qc63jrb1.apps.googleusercontent.com";
	
	
	
	
	private static Map<String, YouTubeService> services = new HashMap<String, YouTubeService>();
	private static Map<String, UploadThread> threads = new HashMap<String, UploadThread>();
	


	private static int accountIndex = 0;
	private static int videoNumberPerAccount = 0;
	public static final List<Output> videoOutputList = new ArrayList<Output>();
	
	/**
	 * YouTubeUploadClient is a sample command line application that
	 * demonstrates how to upload large media files to youtube. This sample uses
	 * resumable upload feature to upload large media.
	 *
	 * @param args
	 *            Used to pass the username and password of a test account.
	 * @throws InterruptedException 
	 */
	public static void main(String[] args) throws InterruptedException {
		String tempFirst = "input.csv";
		String tempSecond = "description";
		String tempThird = "account.csv";

		final String first = tempFirst;
		final String second = tempSecond;
		final String third = tempThird; // number of video each account
		final String output = "output.txt";
		
		SimpleCommandLineParser parser = new SimpleCommandLineParser(args);
		videoNumberPerAccount = 0;
		try {
			videoNumberPerAccount = Integer.parseInt(parser.getValue("videonumber", "h"));
		} catch (NumberFormatException e) {
			videoNumberPerAccount = 0;
		}
		boolean help = parser.containsKey("help", "h");
		

		if (help || videoNumberPerAccount == 0) {
			printUsage();
			System.exit(1);
		}

		System.out.println("Starting Youtube Uploading Application...");

		File directory = new File(second);
		final File[] descriptionFiles = directory.listFiles(new FilenameFilter() {
			
			@Override
			public boolean accept(File dir, String name) {
				if (name.equals(".DS_Store")) {
					return false;
				}
				return true;
			}
		});

		final List<Account> accountList = new ArrayList<Account>();

		// read csv file for each rows
		CSV csv = CSV.separator(',') // delimiter of fields
				.noQuote()
				.charset(StandardCharsets.UTF_8)
				.create(); // new instance is immutable

		csv.read(third, new CSVReadProc() {
			public void procRow(int rowIndex, String... values) {
				if (rowIndex == 0) {
					return;
				}

				Account acc = new Account();
				acc.username = values[0];
				acc.password = values[1];
				acc.developerKey = values[2];
				try {
					acc.delay = Integer.parseInt(values[3]);
				} catch (NumberFormatException e) {
					acc.delay = 30; // default
				}
				accountList.add(acc);
			}
		});

		if (accountList.size() == 0) {
			System.out.println("Account not found.");
			System.out.println("Stopped uploading videos.");
			return;
		}

		accountIndex = 0;

		csv.read(first, new CSVReadProc() {
			public void procRow(int rowIndex, String... values) {
				if (rowIndex == 0) {
					return;
				}
				
				if (accountList.size() <= accountIndex) {
					accountIndex = accountList.size() - 1;
				}
				
				
				if (!services.containsKey(accountList.get(accountIndex).developerKey)) {
					services.put(accountList.get(accountIndex).developerKey, 
							new YouTubeService(APP_NAME, accountList.get(accountIndex).developerKey));
				}
				
				YouTubeService service = services.get(accountList.get(accountIndex).developerKey); 
				
				try {
					
					service.setUserCredentials(accountList.get(accountIndex).username, 
							accountList.get(accountIndex).password);
				} catch (AuthenticationException e) {
					System.out.println(e.getMessage());
					return;
				}
				
				
				if (threads.get(accountList.get(accountIndex).username) == null) {
					UploadThread thread = new UploadThread();
					thread.service = service;
					thread.videoFileNames = new ArrayList<String>();
					thread.descriptionFiles = descriptionFiles;
					thread.inputDescription = values[1];
					thread.videoOutputList = videoOutputList;
					thread.url = values[2];
					thread.youtubeUsername = accountList.get(accountIndex).username;
					thread.delay = accountList.get(accountIndex).delay;
					threads.put(accountList.get(accountIndex).username, thread);
				}
				
				UploadThread thread = threads.get(accountList.get(accountIndex).username);
				thread.videoFileNames.add(values[0]);

				
				accountIndex = rowIndex / videoNumberPerAccount;
				
			}
		});
		
		List<Thread> listThreads = new ArrayList<Thread>(); 
		for (String username : threads.keySet()) {
			UploadThread uploadThread = threads.get(username);
			Thread thread = new Thread(uploadThread);
			thread.start();
			listThreads.add(thread);
			
		}
		for (Thread thread : listThreads) {
			thread.join();
		}
		
		csv.write(output, new CSVWriteProc() {
			public void process(CSVWriter out) {
				out.writeNext("videolink,youtubeaccount,videotitle");
				for (Output output : videoOutputList) {
					out.writeNext("https://www.youtube.com/watch?v=" + output.videoId + "," + output.youtubeAccount + "," + output.videoTitle);
				}
			}
		});
		System.exit(0);
	}

	/**
	 * Shows the usage of how to run the sample from the command-line.
	 */
	private static void printUsage() {
		System.out.println("Usage: java YouTubeUploadClient.jar -jar"
				+ " --videonumber <video number each account can upload>");
	}

	
}
