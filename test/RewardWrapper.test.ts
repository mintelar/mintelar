import { expect } from "chai";
import { network } from "hardhat";

const { ethers } = await network.create();

describe("RewardWrapper", function () {
  let token: any;
  let wrapper: any;
  let admin: any;
  let executor: any;
  let pauser: any;
  let recipient1: any;
  let recipient2: any;
  let recipient3: any;
  let attacker: any;

  const MAX_RECIPIENTS = 10;
  const AMOUNT_PER_RECIPIENT = ethers.parseEther("10");
  const MINTER_ROLE = ethers.keccak256(ethers.toUtf8Bytes("MINTER_ROLE"));
  const ADMIN_ROLE = ethers.keccak256(ethers.toUtf8Bytes("ADMIN_ROLE"));
  const EXECUTOR_ROLE = ethers.keccak256(ethers.toUtf8Bytes("EXECUTOR_ROLE"));
  const PAUSER_ROLE = ethers.keccak256(ethers.toUtf8Bytes("PAUSER_ROLE"));

  function generateRewardId(
    courseId: number,
    groupId: number,
    key: string
  ): string {
    return ethers.keccak256(
      ethers.toUtf8Bytes(`${courseId}:${groupId}:${key}`)
    );
  }

  beforeEach(async function () {
    [admin, executor, pauser, recipient1, recipient2, recipient3, attacker] =
      await ethers.getSigners();

    const HiloToken = await ethers.getContractFactory("HiloToken");
    token = await HiloToken.deploy(admin.address);
    await token.waitForDeployment();

    const RewardWrapper = await ethers.getContractFactory("RewardWrapper");
    wrapper = await RewardWrapper.deploy(
      await token.getAddress(),
      admin.address,
      MAX_RECIPIENTS
    );
    await wrapper.waitForDeployment();

    await token.grantRole(MINTER_ROLE, await wrapper.getAddress());
    await wrapper.grantExecutor(executor.address);
    await wrapper.grantPauser(pauser.address);
  });

  describe("Deployment", function () {
    it("should set correct token address", async function () {
      expect(await wrapper.getTokenAddress()).to.equal(await token.getAddress());
    });

    it("should set correct maxRecipientsPerReward", async function () {
      expect(await wrapper.maxRecipientsPerReward()).to.equal(MAX_RECIPIENTS);
    });

    it("should grant ADMIN_ROLE to deployer", async function () {
      expect(await wrapper.hasRole(ADMIN_ROLE, admin.address)).to.be.true;
    });

    it("should grant EXECUTOR_ROLE to executor", async function () {
      expect(await wrapper.hasRole(EXECUTOR_ROLE, executor.address)).to.be.true;
    });

    it("should grant PAUSER_ROLE to pauser", async function () {
      expect(await wrapper.hasRole(PAUSER_ROLE, pauser.address)).to.be.true;
    });
  });

  describe("processReward - Happy Path", function () {
    it("should mint tokens to all recipients", async function () {
      const rewardId = generateRewardId(1, 100, "key-1");
      const recipients = [recipient1.address, recipient2.address, recipient3.address];

      await wrapper
        .connect(executor)
        .processReward(rewardId, recipients, AMOUNT_PER_RECIPIENT);

      expect(await token.balanceOf(recipient1.address)).to.equal(
        AMOUNT_PER_RECIPIENT
      );
      expect(await token.balanceOf(recipient2.address)).to.equal(
        AMOUNT_PER_RECIPIENT
      );
      expect(await token.balanceOf(recipient3.address)).to.equal(
        AMOUNT_PER_RECIPIENT
      );
    });

    it("should mark reward as processed", async function () {
      const rewardId = generateRewardId(1, 100, "key-1");
      const recipients = [recipient1.address];

      await wrapper
        .connect(executor)
        .processReward(rewardId, recipients, AMOUNT_PER_RECIPIENT);

      expect(await wrapper.hasRewardBeenProcessed(rewardId)).to.be.true;
    });

    it("should emit RewardProcessed event", async function () {
      const rewardId = generateRewardId(1, 100, "key-1");
      const recipients = [recipient1.address, recipient2.address];

      await expect(
        wrapper
          .connect(executor)
          .processReward(rewardId, recipients, AMOUNT_PER_RECIPIENT)
      )
        .to.emit(wrapper, "RewardProcessed")
        .withArgs(
          rewardId,
          recipients,
          AMOUNT_PER_RECIPIENT,
          executor.address
        );
    });

    it("should work with 1 recipient", async function () {
      const rewardId = generateRewardId(1, 100, "key-1");
      const recipients = [recipient1.address];

      await wrapper
        .connect(executor)
        .processReward(rewardId, recipients, AMOUNT_PER_RECIPIENT);

      expect(await token.balanceOf(recipient1.address)).to.equal(
        AMOUNT_PER_RECIPIENT
      );
    });

    it("should work with variable number of recipients", async function () {
      const rewardId = generateRewardId(1, 100, "key-1");
      const recipients = [recipient1.address, recipient2.address];

      await wrapper
        .connect(executor)
        .processReward(rewardId, recipients, AMOUNT_PER_RECIPIENT);

      expect(await token.balanceOf(recipient1.address)).to.equal(
        AMOUNT_PER_RECIPIENT
      );
      expect(await token.balanceOf(recipient2.address)).to.equal(
        AMOUNT_PER_RECIPIENT
      );
    });
  });

  describe("processReward - Access Control", function () {
    it("should revert when caller has no EXECUTOR_ROLE", async function () {
      const rewardId = generateRewardId(1, 100, "key-1");
      const recipients = [recipient1.address];

      await expect(
        wrapper
          .connect(attacker)
          .processReward(rewardId, recipients, AMOUNT_PER_RECIPIENT)
      ).to.be.revertedWithCustomError(wrapper, "AccessControlUnauthorizedAccount");
    });

    it("should allow admin to process rewards (has EXECUTOR_ROLE via admin)", async function () {
      const rewardId = generateRewardId(1, 100, "key-1");
      const recipients = [recipient1.address];

      // Admin does NOT have EXECUTOR_ROLE by default
      await expect(
        wrapper
          .connect(admin)
          .processReward(rewardId, recipients, AMOUNT_PER_RECIPIENT)
      ).to.be.revertedWithCustomError(wrapper, "AccessControlUnauthorizedAccount");
    });
  });

  describe("processReward - Pausable", function () {
    it("should revert when paused", async function () {
      await wrapper.connect(pauser).pause();

      const rewardId = generateRewardId(1, 100, "key-1");
      const recipients = [recipient1.address];

      await expect(
        wrapper
          .connect(executor)
          .processReward(rewardId, recipients, AMOUNT_PER_RECIPIENT)
      ).to.be.revertedWithCustomError(wrapper, "EnforcedPause");
    });

    it("should work after unpause", async function () {
      await wrapper.connect(pauser).pause();
      await wrapper.connect(pauser).unpause();

      const rewardId = generateRewardId(1, 100, "key-1");
      const recipients = [recipient1.address];

      await wrapper
        .connect(executor)
        .processReward(rewardId, recipients, AMOUNT_PER_RECIPIENT);

      expect(await token.balanceOf(recipient1.address)).to.equal(
        AMOUNT_PER_RECIPIENT
      );
    });
  });

  describe("processReward - Duplicate Prevention", function () {
    it("should revert on duplicate rewardId", async function () {
      const rewardId = generateRewardId(1, 100, "key-1");
      const recipients = [recipient1.address];

      await wrapper
        .connect(executor)
        .processReward(rewardId, recipients, AMOUNT_PER_RECIPIENT);

      await expect(
        wrapper
          .connect(executor)
          .processReward(rewardId, recipients, AMOUNT_PER_RECIPIENT)
      ).to.be.revertedWith("Reward already processed");
    });

    it("should allow different rewardIds for same group", async function () {
      const recipients = [recipient1.address];

      const rewardId1 = generateRewardId(1, 100, "key-1");
      const rewardId2 = generateRewardId(1, 100, "key-2");

      await wrapper
        .connect(executor)
        .processReward(rewardId1, recipients, AMOUNT_PER_RECIPIENT);
      await wrapper
        .connect(executor)
        .processReward(rewardId2, recipients, AMOUNT_PER_RECIPIENT);

      expect(await token.balanceOf(recipient1.address)).to.equal(
        AMOUNT_PER_RECIPIENT * 2n
      );
    });
  });

  describe("processReward - Validation", function () {
    it("should revert on empty recipients", async function () {
      const rewardId = generateRewardId(1, 100, "key-1");

      await expect(
        wrapper
          .connect(executor)
          .processReward(rewardId, [], AMOUNT_PER_RECIPIENT)
      ).to.be.revertedWith("Empty recipients");
    });

    it("should revert on too many recipients", async function () {
      const rewardId = generateRewardId(1, 100, "key-1");
      const addresses = Array(MAX_RECIPIENTS + 1).fill(recipient1.address);

      await expect(
        wrapper
          .connect(executor)
          .processReward(rewardId, addresses, AMOUNT_PER_RECIPIENT)
      ).to.be.revertedWith("Too many recipients");
    });

    it("should revert on zero address", async function () {
      const rewardId = generateRewardId(1, 100, "key-1");
      const recipients = [ethers.ZeroAddress];

      await expect(
        wrapper
          .connect(executor)
          .processReward(rewardId, recipients, AMOUNT_PER_RECIPIENT)
      ).to.be.revertedWith("Invalid recipient");
    });

    it("should revert on duplicate wallets", async function () {
      const rewardId = generateRewardId(1, 100, "key-1");
      const recipients = [recipient1.address, recipient1.address];

      await expect(
        wrapper
          .connect(executor)
          .processReward(rewardId, recipients, AMOUNT_PER_RECIPIENT)
      ).to.be.revertedWith("Duplicate recipients");
    });
  });

  describe("Admin Functions", function () {
    describe("setMaxRecipientsPerReward", function () {
      it("should allow admin to change max", async function () {
        await wrapper.connect(admin).setMaxRecipientsPerReward(20);
        expect(await wrapper.maxRecipientsPerReward()).to.equal(20);
      });

      it("should revert when non-admin tries to change max", async function () {
        await expect(
          wrapper.connect(executor).setMaxRecipientsPerReward(20)
        ).to.be.revertedWithCustomError(wrapper, "AccessControlUnauthorizedAccount");
      });

      it("should revert when max < 1", async function () {
        await expect(
          wrapper.connect(admin).setMaxRecipientsPerReward(0)
        ).to.be.revertedWith("Max must be >= 1");
      });
    });

    describe("Role Management", function () {
      it("should allow admin to grant EXECUTOR_ROLE", async function () {
        await wrapper.connect(admin).grantExecutor(attacker.address);
        expect(await wrapper.hasRole(EXECUTOR_ROLE, attacker.address)).to.be
          .true;
      });

      it("should allow admin to revoke EXECUTOR_ROLE", async function () {
        await wrapper.connect(admin).revokeExecutor(executor.address);
        expect(await wrapper.hasRole(EXECUTOR_ROLE, executor.address)).to.be
          .false;
      });

      it("should allow admin to grant PAUSER_ROLE", async function () {
        await wrapper.connect(admin).grantPauser(attacker.address);
        expect(await wrapper.hasRole(PAUSER_ROLE, attacker.address)).to.be.true;
      });

      it("should allow admin to revoke PAUSER_ROLE", async function () {
        await wrapper.connect(admin).revokePauser(pauser.address);
        expect(await wrapper.hasRole(PAUSER_ROLE, pauser.address)).to.be.false;
      });

      it("should revert when non-admin manages roles", async function () {
        await expect(
          wrapper.connect(executor).grantExecutor(attacker.address)
        ).to.be.revertedWithCustomError(wrapper, "AccessControlUnauthorizedAccount");
      });
    });

    describe("Pause / Unpause", function () {
      it("should allow pauser to pause", async function () {
        await wrapper.connect(pauser).pause();
        expect(await wrapper.paused()).to.be.true;
      });

      it("should allow pauser to unpause", async function () {
        await wrapper.connect(pauser).pause();
        await wrapper.connect(pauser).unpause();
        expect(await wrapper.paused()).to.be.false;
      });

      it("should revert when non-pauser tries to pause", async function () {
        await expect(
          wrapper.connect(executor).pause()
        ).to.be.revertedWithCustomError(wrapper, "AccessControlUnauthorizedAccount");
      });

      it("should revert when non-pauser tries to unpause", async function () {
        await wrapper.connect(pauser).pause();
        await expect(
          wrapper.connect(executor).unpause()
        ).to.be.revertedWithCustomError(wrapper, "AccessControlUnauthorizedAccount");
      });
    });
  });
});
