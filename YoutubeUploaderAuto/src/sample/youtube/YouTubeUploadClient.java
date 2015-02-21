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
	
	/**
	 * The URL used to resumable upload
	 */
	public static final String RESUMABLE_UPLOAD_URL = "http://uploads.gdata.youtube.com/resumable/feeds/api/users/default/uploads";
	
	private static final String VIDEO_FILE_FORMAT = "video/*";
	
	/** Max size for each upload chunk */
	private static final int DEFAULT_CHUNK_SIZE = 10000000;
	
	
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
//					thread.service = service;
//					thread.videoFileNames = new ArrayList<String>();
//					thread.descriptionFiles = descriptionFiles;
//					thread.inputDescription = values[1];
//					thread.videoOutputList = videoOutputList;
//					thread.url = values[2];
					thread.youtubeUsername = accountList.get(accountIndex).username;
					threads.put(accountList.get(accountIndex).username, thread);
				}
				
				try {
					ResumableGDataFileUploader uploader = prepareVideoUploader(
							service,
							"videos/"+values[0], 
							descriptionFiles,
							values[1],
							values[0].substring(0, values[0].length() - 4) // title removed .avi
								.replace("_", " ")
								.replace("-", " "),
							videoOutputList,
							values[2],
							accountList.get(accountIndex).username);
					
					UploadThread thread = threads.get(accountList.get(accountIndex).username);
					thread.uploaders.add(uploader);
					Thread.sleep(accountList.get(accountIndex).delay * 1000);
//					new Thread(thread).start();
				} catch (IOException e) {
					// Communications error
					System.err
							.println("There was a problem communicating with the service.");
					e.printStackTrace();
				} catch (ServiceException se) {
					System.out.println("Sorry, your upload was invalid:");
					System.out.println(se.getResponseBody());
					se.printStackTrace();
				} catch (InterruptedException ie) {
					System.out.println("Upload interrupted");
				}

				
				accountIndex = rowIndex / videoNumberPerAccount;
				
			}
		});
		
		List<Thread> listThreads = new ArrayList<Thread>(); 
		for (String username : threads.keySet()) {
			UploadThread uploadThread = threads.get(username);
			Thread thread = new Thread(uploadThread);
			thread.start();
			System.out.println("dddd");
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

	private static ResumableGDataFileUploader prepareVideoUploader(
			YouTubeService service, 
			String videoFilePath,
			File[] descriptionFiles, 
			String inputDescription, 
			String title,
			List<Output> videoOutputList, 
			String url, 
			String youtubeUsername) throws IOException, ServiceException,
				InterruptedException {
		
		System.out.println("\nProcessing... Email:" 
				+ youtubeUsername +" Video:"+videoFilePath);
		
		File videoFile = new File(videoFilePath);
		if (!videoFile.exists()) {
			System.out.println("Sorry, that video doesn't exist.");
			return null;
		}
		String randomDescription = "";
		if (descriptionFiles.length > 0) {
			final Random random = new Random();
			int randomInt = randomInteger(0, descriptionFiles.length - 1,
					random);
			
			File descriptionFile = descriptionFiles[randomInt];
			randomDescription = Files.toString(descriptionFile, Charsets.UTF_8);
			
			if (randomDescription != null) {
				randomDescription = randomDescription.replaceAll("\\[TITLE\\]",
						title);
				randomDescription = randomDescription.replaceAll("http://url",
						url);
			}
		}

		MediaFileSource ms = new MediaFileSource(videoFile, VIDEO_FILE_FORMAT);
		String videoTitle = title;
		VideoEntry newEntry = new VideoEntry();
		YouTubeMediaGroup mg = newEntry.getOrCreateMediaGroup();
		mg.addCategory(new MediaCategory(YouTubeNamespace.CATEGORY_SCHEME,
				"Tech"));
		mg.setTitle(new MediaTitle());
		mg.getTitle().setPlainTextContent(videoTitle);
		mg.setKeywords(new MediaKeywords());
		mg.getKeywords().addKeyword("gdata-test");
		mg.setDescription(new MediaDescription());
		mg.getDescription().setPlainTextContent(
				inputDescription + "\n" + randomDescription);
		
		ResumableGDataFileUploader uploader = new ResumableGDataFileUploader.Builder(
				service, new URL(RESUMABLE_UPLOAD_URL), ms, newEntry)
				.title(videoTitle)
				.chunkSize(DEFAULT_CHUNK_SIZE).build();
		return uploader;
	}
	
	
	
	/**
	 * Shows the usage of how to run the sample from the command-line.
	 */
	private static void printUsage() {
		System.out.println("Usage: java YouTubeUploadClient.jar -jar"
				+ " --videonumber <video number each account can upload>");
	}

	
	private static int randomInteger(int aStart, int aEnd, Random aRandom){
        if (aStart > aEnd) {
          throw new IllegalArgumentException("Start cannot exceed End.");
        }
        //get the range, casting to long to avoid overflow problems
        long range = (long)aEnd - (long)aStart + 1;
        // compute a fraction of the range, 0 <= frac < range
        long fraction = (long)(range * aRandom.nextDouble());
        int randomNumber =  (int)(fraction + aStart);
        return randomNumber;
      }

}
