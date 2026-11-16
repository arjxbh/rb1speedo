# Pairing codes from your laptop using IAM / SigV4

The recommended owner workflow is a separate Lambda Function URL authenticated by AWS IAM. AWS validates Signature Version 4 before invoking the handler. It is not an anonymous code-generation API and it does not implement custom signature checking on EC2. The existing phone API, its enrollment format, and Android 1.6.0 stay unchanged.

Once the one-time owner stack deployment is complete:

```sh
npm run pair
# Or select an existing AWS profile explicitly:
npm run pair -- --profile YOUR_PROFILE
```

The helper discovers `RB1OwnerStack`'s URL through CloudFormation, resolves credentials through the AWS SDK's normal profile/provider chain, signs `POST /pairing-codes` for the `lambda` service in `us-east-1`, and prints the code and phone API address. Temporary credentials/session tokens are supported. It does not export or print AWS credentials or signed headers. No SSM session, SSH, new DNS record, or new phone APK is needed for routine pairing.

In the phone's Camera screen, enter the displayed code and `https://dashcam.midlifemotor.club`, then tap Pair this phone on unmetered Wi-Fi. The code expires after ten minutes and can enroll exactly one device. If a response was lost, run the owner command again to issue a new code.

## One-time deployment

Use Node 24+, AWS CLI v2, and an authorized profile in account `294281109519`, region `us-east-1`. From the updated repository:

```sh
npm ci --ignore-scripts
npm run deploy:owner -- --profile YOUR_PROFILE
```

This prepares only. It builds a locked, vendored Lambda package, runs tests, synthesizes and diffs `RB1OwnerStack`, and prepares a CloudFormation change set without execution. It discovers the existing RB1 metadata table from live stack outputs and records the RB1 template/outputs plus the full shared-host/four-application snapshot. No copy/pasted EC2 IDs or table names are used for deployment.

Review the exact changes:

```sh
less infra/owner-review.json
```

Then execute within one hour using the same profile:

```sh
npm run deploy:owner -- --profile YOUR_PROFILE --execute
```

Omit `--profile YOUR_PROFILE` throughout if your normal default profile or `AWS_PROFILE` is already correct. The script verifies the exact reviewed template/change list, rejects removals and replacements, and compares the existing infrastructure before/after. It verifies that the new Function URL requires IAM and returns HTTP 403 to an unsigned request. It does not issue a pairing code as a deployment health check.

The owner stack can create only its own Lambda, IAM execution role/policy, IAM-authenticated Function URL, retained seven-day log group, and CDK metadata. Its guard rejects EC2, SSM, DNS, table, and bucket resources, attachment to an existing role, and a public Function URL. There is no EC2 application release, Caddy reload, instance restart, or change to Piggy Ledger, Midlife Motors, Kaedama, Cranky Goose, or the existing RB1 CloudFormation template. Continue to use the original guarded `npm run deploy` path only for changes to the EC2-backed phone API.

If a local execution command was interrupted or appears stuck after CloudFormation completes, verify the retained review without submitting the change set again:

```sh
npm run deploy:owner -- --profile YOUR_PROFILE --verify
```

This read-only path works after the one-hour execution window and compares the deployed template, original dependency snapshot, Function URL IAM configuration, unsigned-request rejection, and existing site/API health. Ctrl+C stops a local waiter; it does not cancel an AWS deployment already submitted. The scripts choose create/update waiters from stack state before execution and print which waiter is running.

## Owner permissions

AWS now requires **both** `lambda:InvokeFunctionUrl` and `lambda:InvokeFunction` for new Function URLs. The signed request's caller must be in the same AWS account, pass normal IAM authorization for the specific function, and have access to describe `RB1OwnerStack` so the helper can discover the URL.

The owner stack outputs `CallerPolicy`, containing least-privilege invocation permissions for its exact function ARN and permission to describe only this owner stack for endpoint discovery. The `InvokeFunction` grant is conditional on `lambda:InvokedViaFunctionUrl=true`; the URL grant requires `lambda:FunctionUrlAuthType=AWS_IAM`. Attach that policy to a designated owner identity through your usual IAM management if your existing identity lacks those permissions. An administrator profile may already have them. The deployment does not silently modify your personal IAM user/role, another application's role, or grant public/cross-account invocation.

The Lambda execution role is a new role trusted only by Lambda. Its DynamoDB access is limited to `PutItem` and `UpdateItem` on the existing RB1 metadata table with leading keys `PAIR#*` and `PAIR-RATE#*`. It cannot read or modify device refresh/access credentials, clip metadata, or videos. Logs can be written only to its own log group.

## Code generation and privacy

- Only `POST /pairing-codes` with an empty JSON object is accepted. The caller cannot select a target table, prefix, token value or expiry.
- Codes are generated from 256 bits of fresh cryptographic randomness and returned only in the HTTPS response with `Cache-Control: no-store`. DynamoDB stores the SHA-256 verifier, expiry, unused flag, and issuing principal. This matches the original EC2 API's one-time enrollment transaction.
- A global DynamoDB-backed limit permits twelve codes per UTC hour. Counter items expire automatically. Failed/lost issuance attempts can consume a slot; this protects the endpoint from accidental loops.
- Application audit logs record only the issuing principal, request ID, expiry and success/failure type. Request bodies, raw codes, verifiers, AWS access keys/session tokens and signatures are not logged.
- The phone still stores only its own Keystore-encrypted device refresh credential and short-lived API access. AWS credentials never move to the phone or EC2 pairing handler. All existing refresh rotation, expiry and device-revocation behavior remains unchanged.

This adds usage-based Lambda compute, DynamoDB operations, and retained logs. There is no provisioned concurrency, VPC, NAT gateway or API Gateway for this occasional owner operation. Review actual AWS billing rather than treating the video-storage estimate as the entire account bill.

## Troubleshooting

- `RB1OwnerStack does not exist`: deploy the owner stack once using the guarded commands above.
- HTTP 403: confirm the profile/account and both invocation permissions. An unsigned browser visit is expected to receive 403; use `npm run pair`.
- HTTP 429: wait for the next UTC hour; the issuance limit was reached.
- Expired AWS profile/SSO: renew your existing AWS login and retry. Do not copy long-lived AWS keys into the app.
- No code after a network error: retry the command. Each successful response yields a fresh one-use code; codes from lost responses simply expire.

The original Session Manager `admin.cjs pair` command remains an operator fallback, not the required routine workflow.

Sources: [Lambda Function URL IAM authentication](https://docs.aws.amazon.com/lambda/latest/dg/urls-auth.html), [SigV4 invocation and trusted IAM request context](https://docs.aws.amazon.com/lambda/latest/dg/urls-invocation.html).
