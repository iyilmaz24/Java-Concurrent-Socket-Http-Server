# CodeCrafters HTTP Server
[![progress-banner](https://backend.codecrafters.io/progress/http-server/c9a21015-9f29-4d64-bbdf-6f32c27c23a2)](https://app.codecrafters.io/users/codecrafters-bot?r=2qF)

This project implements a HTTP/1.1 server, it handles GET and POST requests, serving static files, echoing messages, returning user-agent information, and supporting gzip compression.

## Technologies Used
* **Java 23:** The programming language used for the server implementation.
* **Maven:** Build automation tool used for compiling and packaging the project.
* **Java Loom Virtual Threads:** Used for efficient handling of concurrent client requests.

## Features
* **Handles GET requests:**  Serves static files from a specified directory and responds to `/echo/{message}` and `/user-agent` endpoints.
* **Handles POST requests:** Creates files specified in `/files/{filename}` endpoint.  File content is taken from the request body.
* **Static File Serving:** Serves files from a user-specified directory.
* **Gzip Compression:** Supports gzip compression for responses if the client requests it.
* **Error Handling:** Returns appropriate HTTP error codes (404 Not Found, 403 Forbidden, 500 Internal Server Error) for various error conditions.
* **Multi-Client Handling:** Uses virtual threads to handle multiple client connections concurrently.

## Usage
1.  **Set up the file directory (optional):**  If you want the server to serve static files or handle file uploads, create a directory and specify its path using the `--directory <path>` command-line argument when running the server.
2.  **Run the server:** Execute the `your_program.sh` script. This will compile and run the server, listening on port 4221.

Example (serving files from `/path/to/files`):

```bash
./your_program.sh --directory /path/to/files
```

To test the server, use a tool like `curl` or a web browser.

Example using curl:

```bash
curl http://localhost:4221/echo/hello
curl -H "Accept-Encoding: gzip" http://localhost:4221/echo/hello  # test gzip compression
curl -X POST -H "Content-Type: text/plain" -H "Content-Length: 13" -d "Hello, world!" http://localhost:4221/files/test.txt
```

*README.md was made with [Etchr](https://etchr.dev)*