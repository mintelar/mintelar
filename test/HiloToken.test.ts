import { expect } from "chai";
import { network } from "hardhat";

const { ethers } = await network.create();

describe("HiloToken", function () {
  let token: any;
  let owner: any;
  let addr1: any;
  let addr2: any;

  const MINTER_ROLE = ethers.keccak256(ethers.toUtf8Bytes("MINTER_ROLE"));

  beforeEach(async function () {
    [owner, addr1, addr2] = await ethers.getSigners();
    const HiloToken = await ethers.getContractFactory("HiloToken");
    token = await HiloToken.deploy(owner.address);
    await token.waitForDeployment();
  });

  describe("Deployment", function () {
    it("should set correct name and symbol", async function () {
      expect(await token.name()).to.equal("HILO Token");
      expect(await token.symbol()).to.equal("HILO");
    });

    it("should set 18 decimals", async function () {
      expect(await token.decimals()).to.equal(18);
    });

    it("should grant DEFAULT_ADMIN_ROLE to deployer", async function () {
      const DEFAULT_ADMIN_ROLE = ethers.ZeroHash;
      expect(await token.hasRole(DEFAULT_ADMIN_ROLE, owner.address)).to.be
        .true;
    });

    it("should grant MINTER_ROLE to deployer", async function () {
      expect(await token.hasRole(MINTER_ROLE, owner.address)).to.be.true;
    });

    it("should start with zero total supply", async function () {
      expect(await token.totalSupply()).to.equal(0);
    });
  });

  describe("mint", function () {
    it("should mint tokens when caller has MINTER_ROLE", async function () {
      const amount = ethers.parseEther("100");
      await token.mint(addr1.address, amount);
      expect(await token.balanceOf(addr1.address)).to.equal(amount);
      expect(await token.totalSupply()).to.equal(amount);
    });

    it("should revert when caller has no MINTER_ROLE", async function () {
      const amount = ethers.parseEther("100");
      await expect(
        token.connect(addr1).mint(addr1.address, amount)
      ).to.be.revertedWithCustomError(token, "AccessControlUnauthorizedAccount");
    });

    it("should mint to multiple addresses", async function () {
      const amount = ethers.parseEther("50");
      await token.mint(addr1.address, amount);
      await token.mint(addr2.address, amount);
      expect(await token.balanceOf(addr1.address)).to.equal(amount);
      expect(await token.balanceOf(addr2.address)).to.equal(amount);
      expect(await token.totalSupply()).to.equal(amount * 2n);
    });

    it("should emit Transfer event on mint", async function () {
      const amount = ethers.parseEther("10");
      await expect(token.mint(addr1.address, amount))
        .to.emit(token, "Transfer")
        .withArgs(ethers.ZeroAddress, addr1.address, amount);
    });
  });

  describe("transfer", function () {
    it("should transfer tokens between accounts", async function () {
      const amount = ethers.parseEther("100");
      await token.mint(owner.address, amount);
      await token.transfer(addr1.address, amount);
      expect(await token.balanceOf(addr1.address)).to.equal(amount);
      expect(await token.balanceOf(owner.address)).to.equal(0);
    });
  });
});
