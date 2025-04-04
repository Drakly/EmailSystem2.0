# Email System 2.0 - Internal Messaging System

## Overview
Email System 2.0 is an internal messaging platform designed for organizations to facilitate communication between users within the system. Unlike traditional email systems, this platform does not send messages to external email providers (like Gmail, Yahoo, etc.), but instead routes all communication internally within the application.

## Features
- **Internal Message Routing**: All emails are sent and received within the system
- **User-friendly Interface**: Modern UI with intuitive controls
- **Attachment Support**: Send files and documents to other users
- **Draft Management**: Save and edit drafts before sending
- **Email Organization**: Inbox, Sent, Drafts, and Trash folders
- **Bulk Operations**: Move multiple emails to trash, delete them permanently
- **Rich Text Editing**: Format your emails with a built-in editor

## Technical Implementation
- **Backend**: Java Spring Boot application
- **Database**: MySQL database for storing messages, users, and attachments
- **Frontend**: Thymeleaf templates with Bootstrap for responsive design
- **Security**: Spring Security for authentication and authorization

## Internal Communication Only
This system is explicitly designed for internal communication. Important notes:
- Users can only send messages to other users registered in the system
- No external email provider configuration is required
- Emails do not leave the system's database
- Ideal for organizations concerned with privacy and security

## Getting Started
1. Configure the application.properties file with your database settings
2. Run the application using Spring Boot
3. Register users in the system
4. Start communicating internally

## Development
To modify or extend this system:
1. Clone the repository
2. Import as a Maven project
3. Make your changes
4. Run tests to ensure functionality
5. Deploy to your environment

## Requirements
- Java 17 or higher
- MySQL database
- Maven for dependency management

## License
This project is licensed under the MIT License - see the LICENSE file for details. 