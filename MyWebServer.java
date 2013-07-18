/*Jonathan Lehman*/
/*Mar 2, 2013*/

import java.io.*; 
import java.net.*; 
import java.util.*;
import java.text.*;

class MyWebServer { 
	//determines whether test outpit is printed
	static boolean printTestOutput = false;
	static int timeout = 30000; //change to 3 second if running a script or browser, longer if using telnet manually
	
  	public static void main(String argv[]) throws Exception { 
		
		//check arguments, arg0:portNum, arg1:rootDir
		int portNum = 0;
		String rootDir = "";
		
		//variables
		boolean firstPass = true;
		boolean hasModSince = false;
		boolean badRequest = false;
		String requestLine = "";
		String ifModSinceDate = "";
		String conn = "close";
		String method = "";
		String file = "";
		String errMess = "";
		int status = 200;
		
		//date formatter
		SimpleDateFormat HTTPDateFormat = new SimpleDateFormat("EEE MMM d hh:mm:ss zzz yyyy");
		
		//check args validity then assign
		checkArgs(argv);
		portNum = Integer.parseInt(argv[0]);
		rootDir = argv[1];

		ServerSocket welcomeSocket = null;
		//create welcome socket with user defined port number
		//make sure user has permission to use port
		try {
	  		welcomeSocket = new ServerSocket(portNum); 
		}
		catch(BindException e) {
			System.err.println("\nYou must use a port that you have permission to use (above 1023 or use sudo).\n");
			System.exit(1);
		}

		//run until server process is killed	
      	while(true) { 
			
			
			//wait for client to contact welcome socket
            Socket connectionSocket = welcomeSocket.accept(); 
			
			try {
				do{
					//time to wait before killing (prevents problems when connection persistent)
					connectionSocket.setSoTimeout(timeout);
					//connectionSocket.setLinger(timeout / 2);
					
					//reset vars
					badRequest = false;
					firstPass = true;
					hasModSince = false;
					requestLine = "";
					errMess = "";
					conn = "close";
					ifModSinceDate = "";
					method = "";
					file = "";	
					status = 200;	

					//create input stream and output stream attached to socket
					BufferedReader inFromClient = new BufferedReader(new InputStreamReader(connectionSocket.getInputStream()));
					DataOutputStream  outToClient = new DataOutputStream(connectionSocket.getOutputStream());
			
					if(printTestOutput) {
						System.out.println("Request:");
					}	
			
					//go through rest of request check for any request formatting errors(invalid request)	
					while((requestLine = inFromClient.readLine()) != null && !requestLine.trim().equals("")) {
						if(printTestOutput) {
							System.out.println(requestLine);
						}
					
						StringTokenizer tokenizer = new StringTokenizer(requestLine);
					
						//check
						if(firstPass) {
							firstPass = false;
							//try, if NoSuchElementException, or ParseException then bad request, formatted badly
							try {
								method = tokenizer.nextToken().trim();
								file = tokenizer.nextToken().trim();
				
								if(!tokenizer.nextToken().trim().equals("HTTP/1.1")) {
									if(printTestOutput) {
										System.out.println("http val error");
									}
									badRequest = true;
								}
							}
							catch(NoSuchElementException e) {
								if(printTestOutput) {
									System.out.println("first error");
								}
								badRequest = true;
							}
						}
						else if(requestLine.contains("If-Modified-Since")) {
							hasModSince = true;
						
							//try, if NoSuchElementException, or ParseException then bad request, formatted badly
							try {
								tokenizer.nextToken(":");//in case space between Header and ":"
								//get entire date(aka rest of tokens in line after first token)
								ifModSinceDate = tokenizer.nextToken("\n").substring(1).trim();
				
								//check for valid date format
								if(!HTTPDateFormat.parse(ifModSinceDate).toString().equals((ifModSinceDate))) {
									if(printTestOutput) {
										System.out.println(HTTPDateFormat.parse(ifModSinceDate).toString());
										System.out.println(ifModSinceDate);
										System.out.println("ifmod val error");
									}
									badRequest = true;
								}
							}
							catch(Exception e) {
								if(printTestOutput) {
									System.out.println("ifmod error " + e);
								}
								badRequest = true;
							}
						}
						else if(requestLine.contains("Date")) {
							String theDate = "";
							//try, if NoSuchElementException, or ParseException then bad request, formatted badly
							try {
								tokenizer.nextToken(":");//in case space between Header and ":"
								//get entire date(aka rest of tokens in line after first token)
								theDate = tokenizer.nextToken("\n").substring(1).trim();
							
								//check for valid date format
								if(!HTTPDateFormat.parse(theDate).toString().equals((theDate))) {
									if(printTestOutput) {
										System.out.println("date val error");
									}
									badRequest = true;
								}
							}
							catch(Exception e) {
								if(printTestOutput) {
									System.out.println("date error");
								}
								badRequest = true;
							}
						}
						else if(requestLine.contains("Connection")) {
							//try, if NoSuchElementException, then bad request, formatted badly
							try {
								tokenizer.nextToken(":");//in case space between Header and ":"
								//get entire date(aka rest of tokens in line after first token)
								conn = tokenizer.nextToken().trim();
							
								if(!(conn.equals("close") || conn.equals("keep-alive"))){
									if(printTestOutput) {
										System.out.println("conn val error");
									}
									badRequest = true;
								}
							}
							catch(NoSuchElementException e) {
								if(printTestOutput) {
									System.out.println("conn error");
								}
								badRequest = true;
							}
						}
						else {
							//try, if NoSuchElementException, then bad request, formatted badly
							try {
								//if(tokenizer.hasMoreTokens()){
									tokenizer.nextToken(":");//in case space between Header and ":"
									//get entire date(aka rest of tokens in line after first token)
									tokenizer.nextToken();
									//}
							}
							catch(NoSuchElementException e) {
								if(printTestOutput) {
									System.out.println("any error");
								}
								badRequest = true;
							}
						}
					}
				
					//formatting of filename:
					//ensure that file name valid, no ""//"" or "http://stuff/"
					String fileName = (rootDir + "/" + file).replaceAll("http://.*/", "").replace("//", "/").replaceAll("%20", " ");
					//trim "/" if at end
					if(fileName.substring(fileName.length() - 1).equals("/")) {
						fileName = fileName.substring(0, fileName.length() - 1);
					}
				
					if(printTestOutput) {
						System.out.println(fileName);
					}		
					File f = new File(fileName);
					
					//determine status
					if(badRequest) {
						if(printTestOutput) {
							System.out.println("bad request");
						}
						//400  Bad Request : Returned if the request is not in the proper syntax
						status = 400;
						errMess = "<!DOCTYPE html>\n<HTML>\n<HEAD>\n</HEAD>\n<BODY>\n<H1>\n400 Bad Request\n</H1>\n</BODY>\n</HTML>\n";
					}
					else if(!f.isFile()) {
						//check if directory
						if(f.isDirectory()) {
							//check if index.html exists
							f = new File((rootDir + "/index.html").replaceAll("http://.*/", "").replace("//", "/"));
							
							if(f.isFile()) {
								//return index.html
							}
							else {
								//404 Not Found : Returned when the desired file is not found
								status = 404;
								errMess = "<!DOCTYPE html>\n<HTML>\n<HEAD>\n</HEAD>\n<BODY>\n<H1>\n404 Not Found\n</H1>\n</BODY>\n</HTML>\n";
							}
						}
						else {
							//404 Not Found : Returned when the desired file is not found
							status = 404;
							errMess = "<!DOCTYPE html>\n<HTML>\n<HEAD>\n</HEAD>\n<BODY>\n<H1>\n404 Not Found\n</H1>\n</BODY>\n</HTML>\n";
						}
						
					}
					else if(hasModSince && f.isFile()) {
						//check if file has not been modified
						if(HTTPDateFormat.parse(HTTPDateFormat.format(f.lastModified())).before(HTTPDateFormat.parse(ifModSinceDate))) {
							//304 Not Modified 
							status = 304;
							errMess = "<!DOCTYPE html>\n<HTML>\n<HEAD>\n</HEAD>\n<BODY>\n<H1>\n304 Not Modified\n</H1>\n</BODY>\n</HTML>\n";
						}
					}				
					
					//generate response
					String response = "";
					try {
						response = generateResponse(status, method, conn, f, HTTPDateFormat, errMess, hasModSince);
					}
					catch(Exception e) {
						if(printTestOutput) {
							System.out.println("Generate response error: " + e);
						}
					}
					
					//write response
					outToClient.writeBytes(response.toString()); 
				
					if(printTestOutput) {
						System.out.println("Response:");
						System.out.println(response.toString());
					}
						
				
					if(printTestOutput) {
						System.out.println("Status: " + status);
						System.out.println("Connection: " + conn);
					}
					
					if(printTestOutput && conn.equals("keep-alive")) {
						System.out.println("Persistent conn");
					}
				
				}while(conn.equals("keep-alive"));//for persistent connections
			
				if(printTestOutput) {
					System.out.println("Conn is closed");
				}
				connectionSocket.close();
			}
			catch(Exception e) {
				//socket timeout
				if(printTestOutput) {
					System.out.println("Error: " + e);
				}
				connectionSocket.close();
			}

		} //loops back to beginning of while to wait for another client
	} 
	
	//**********************************************************************
	//HELPER
	
	//check arguments
	public static void checkArgs(String [] args) {
		//check number of arguments
		if(args.length != 2) {
			System.err.println("\nWebserver must have 2 arguments, port number, and root directory.\nexample: \"java MyWebServer 8817 ~/myweb\"\n");
			System.exit(1);
		}
		
		//check that portNum is an integer (valid portNum)
		try {			
			//check that portNum is positive 
			if(Integer.parseInt(args[0]) < 0) {
				System.err.println("\nThe first argument, port number, must be a positive integer.\n");
				System.exit(1);
			}
		} catch (NumberFormatException e) {
		  	// not integer
			System.err.println("\nThe first argument, port number, must be an integer.\n");
			System.exit(1);
		}
		
		//check that rootDir is valid 
		if(args[1].trim().equals("")) {
			System.err.println("\nThe second argument, root directory, must be valid.\n");
			System.exit(1);
		}
	}
	
	//generates and returns response
	public static String generateResponse(int status, String method, String conn, File f, SimpleDateFormat HTTPDateFormat, String errMess, boolean hasModSince) throws Exception {
		String date = HTTPDateFormat.format(new Date()).toString();
		StringBuffer response = new StringBuffer();
				
		//handle get method
		if(method.equals("GET")) {
						
			response.append("HTTP/1.1 " + status + "\nDate: " + date + "\nConnection: " + conn + "\nServer: LehmanJavaWebServer\n");
					
			if(status == 200) {
				response.append("Content-Length: " + f.length() + "\n");
			}
			else {
				response.append("Content-Length: " + errMess.length() + "\n");
			}
					
			//check if need to add last modified date
			if(hasModSince) {
				response.append("Last-Modified: " + HTTPDateFormat.parse(HTTPDateFormat.format(f.lastModified())).toString() + "\n\n");
			}
			else {
				response.append("\n");
			}
					
			if(status == 200) {
				FileInputStream fin = new FileInputStream(f);
				byte fileContent[] = new byte[(int)f.length()];
				fin.read(fileContent);
						
				response.append(new String(fileContent));
			}
			else {
				response.append(errMess);
			}
				
			if(printTestOutput) {
				System.out.println("Response:");
				System.out.println(response.toString());
			}
					
		}
		else if(method.equals("HEAD")) {//handle head method
			response.append("HTTP/1.1 " + status + "\nDate: " + date + "\nConnection: " + conn + "\nServer: LehmanJavaWebServer\n");
					
			//check if need to add last modified date
			if(hasModSince) {
				response.append("Last-Modified: " + HTTPDateFormat.parse(HTTPDateFormat.format(f.lastModified())).toString() + "\n\n");
			}
			else {
				response.append("\n");
			}
				
			if(printTestOutput) {
				System.out.println("Response:");
				System.out.println(response.toString());
			}
		}
		else {
			//return not implemented error (501)
			status = 501;
			errMess = "<!DOCTYPE html>\n<HTML>\n<HEAD>\n</HEAD>\n<BODY>\n<H1>\n501 Not Implemented\n</H1>\n</BODY>\n</HTML>\n";
						
			response.append("HTTP/1.1 " + status + "\nDate: " + date + "\nConnection: " + conn + "\nServer: LehmanJavaWebServer\n");
					
			response.append("Content-Length: " + errMess.length() + "\n");
						
					
			//check if need to add last modified date
			if(hasModSince) {
				response.append("Last-Modified: " + HTTPDateFormat.parse(HTTPDateFormat.format(f.lastModified())).toString() + "\n\n");
			}
			else {
				response.append("\n");
			}
					
			response.append(errMess);
		}
		
		return response.toString();
	}
}