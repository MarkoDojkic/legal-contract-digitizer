# Legal Contract Digitizer™

Legal Contract Digitizer™ is a backend service that automates the extraction, digitization,
and deployment of legal contracts as Ethereum smart contracts. It leverages AI for clause extraction and Solidity code generation, integrates with the Ethereum blockchain for contract deployment, and manages user wallets securely.

## Features

- Upload legal contract text and extract clauses using AI
- Generate Solidity smart contract source code from extracted clauses
- Compile and deploy smart contracts to Ethereum test networks
- Estimate gas costs and invoke contract functions
- Manage user Ethereum wallets securely (`/ethWallets` directory)
- REST API secured with JWT (via Google OAuth2)
- Firebase integration for backend services
- API documentation with Swagger/OpenAPI
- JavaFX desktop UI for contract management

## Technologies Used

- Java **24**
- Spring Boot (Security, WebFlux, Web, Dependency Injection)
- Web3j (Ethereum blockchain integration)
- Firebase Admin SDK
- Lombok
- Jackson (JSON serialization/deserialization)
- Swagger/OpenAPI
- Google Cloud Firestore
- Spring AI integration (OpenAI via WebClient)
- JavaFX (GUI layer)

## Project Structure Overview

```
src/main
└── java/
   ├── config           # Spring Boot configuration (security, beans, Firebase, Swagger)
   ├── controller/      # REST controllers for API endpoints
   ├── exception/       # Custom checked and runtime exceptions
   ├── javafx/          # JavaFX application code
   │   └── controller/  # UI controllers (FXML-bound)
   ├── model/           # Immutable records and enums for contracts, blockchain, wallets
   ├── service/         # Business logic interfaces and implementations
   ├── util/            # Utility classes (AuthSession, HTTP client, JWT filter)
└── resources/          # Static resources
   ├── layout/          # FXML layout files
   └── static/
      ├── audio/        # Audio files (e.g. window-minimize.wav)
      ├── images/       # Image assets
      └── style/        # CSS stylesheets
ethWallets/             # Encrypted user wallet keystore files
```

## Getting Started

### Prerequisites

- Java **17+**, note that the project is built using Java 24
- Maven
- Access to Ethereum testnet node (e.g. [Infura](https://infura.io))
- Firebase Admin SDK credentials
- OpenAI API key (for AI clause extraction and code generation)

### Setup

1. Clone the repository:
   ```bash
   git clone https://github.com/MarkoDojkic/legal-contract-digitizer.git
   cd legal-contract-digitizer
   ```

2. Add Firebase credentials:
    - Place your `firebase-adminsdk-service-account.json` in the `src/main/resources` directory.

3. Configure application properties (`application.yaml`):
    - Set Ethereum node URL (e.g., Infura), OpenAI API key, Google OAuth2 client, etc.

4. Build the project:
   ```bash
   ./mvnw clean install
   ```

5. Run the application:
   ```bash
   ./mvnw spring-boot:run
   ```

## API Documentation

Swagger UI is available at:
```
http://localhost:{server-port}/swagger-ui.html
```

## Security

The API is secured using JWT tokens. Obtain tokens via your authentication flow and include them in the `Authorization` header as `Bearer {token}`.<br>
JWT tokens are issued through Google OAuth2 sign-in.

Ethereum wallets are created, stored, and managed in `/ethWallets`.
Each wallet is an encrypted keystore file.

## License

This project is licensed under the MIT License. See the [LICENSE](https://github.com/MarkoDojkic/legal-contract-digitizer/blob/main/LICENSE) file for details.

*Notes on UI Assets*

The UI design is inspired by Code Lyoko. Some images and sounds used in the project reflect this inspiration.
**Disclaimer:** I do not own any rights to Code Lyoko or its assets. All related copyrights belong to their respective owners.

## Contact

Developed by Marko Dojkić
GitHub: [MarkoDojkic](https://github.com/MarkoDojkic)