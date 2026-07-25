import { network } from "hardhat";

const { ethers } = await network.create();

async function main() {
  const [deployer] = await ethers.getSigners();
  console.log("Deploying with account:", deployer.address);

  // --- 1. Deploy HiloToken ---
  const HiloToken = await ethers.getContractFactory("HiloToken");
  const token = await HiloToken.deploy(deployer.address);
  await token.waitForDeployment();
  const tokenAddress = await token.getAddress();
  console.log("HiloToken deployed to:", tokenAddress);

  // --- 2. Deploy RewardWrapper ---
  const MAX_RECIPIENTS = 50;
  const RewardWrapper = await ethers.getContractFactory("RewardWrapper");
  const wrapper = await RewardWrapper.deploy(
    tokenAddress,
    deployer.address,
    MAX_RECIPIENTS
  );
  await wrapper.waitForDeployment();
  const wrapperAddress = await wrapper.getAddress();
  console.log("RewardWrapper deployed to:", wrapperAddress);

  // --- 3. Grant MINTER_ROLE to wrapper ---
  const MINTER_ROLE = await token.MINTER_ROLE();
  const tx1 = await token.grantRole(MINTER_ROLE, wrapperAddress);
  await tx1.wait();
  console.log("MINTER_ROLE granted to wrapper");

  // --- 4. Revoke MINTER_ROLE from deployer (optional) ---
  const tx2 = await token.renounceRole(MINTER_ROLE, deployer.address);
  await tx2.wait();
  console.log("Deployer renounced MINTER_ROLE");

  // --- 5. Print deployment summary ---
  console.log("\n═══════════════════════════════════════");
  console.log("DEPLOYMENT COMPLETE");
  console.log("═══════════════════════════════════════");
  console.log("Network:          Arbitrum Sepolia");
  console.log("Chain ID:         421614");
  console.log("Admin (deployer):", deployer.address);
  console.log("HiloToken:       ", tokenAddress);
  console.log("RewardWrapper:   ", wrapperAddress);
  console.log("Max Recipients:  ", MAX_RECIPIENTS);
  console.log("═══════════════════════════════════════");
  console.log("\nNext steps:");
  console.log("1. Grant EXECUTOR_ROLE to backend wallet:");
  console.log(`   wrapper.grantExecutor("0x...backendWallet...")`);
  console.log("2. Set HILO_WRAPPER_ADDRESS in backend .env");
  console.log("3. Deploy Java backend");
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error(error);
    process.exit(1);
  });
