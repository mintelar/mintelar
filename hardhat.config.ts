import { configVariable, defineConfig } from "hardhat/config";
import hardhatToolboxMochaEthers from "@nomicfoundation/hardhat-toolbox-mocha-ethers";
import hardhatVerify from "@nomicfoundation/hardhat-verify";

export default defineConfig({
  plugins: [hardhatToolboxMochaEthers, hardhatVerify],
  solidity: {
    version: "0.8.27",
    settings: {
      optimizer: {
        enabled: true,
        runs: 200,
      },
    },
  },
  networks: {
    hardhat: {
      type: "edr-simulated",
    },
    arbitrumSepolia: {
      type: "http",
      chainId: 421614,
      url: configVariable("ARBITRUM_SEPOLIA_RPC_URL"),
      accounts: [configVariable("ADMIN_PRIVATE_KEY")],
    },
  },
  verify: {
    etherscan: {
      apiKey: configVariable("ARBISCAN_API_KEY"),
    },
  },
});
