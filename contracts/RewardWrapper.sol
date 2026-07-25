// SPDX-License-Identifier: MIT
pragma solidity ^0.8.27;

import {AccessControl} from "@openzeppelin/contracts/access/AccessControl.sol";
import {Pausable} from "@openzeppelin/contracts/utils/Pausable.sol";
import {HiloToken} from "./HiloToken.sol";

contract RewardWrapper is AccessControl, Pausable {

    bytes32 public constant ADMIN_ROLE = keccak256("ADMIN_ROLE");
    bytes32 public constant EXECUTOR_ROLE = keccak256("EXECUTOR_ROLE");
    bytes32 public constant PAUSER_ROLE = keccak256("PAUSER_ROLE");

    HiloToken public immutable token;
    uint256 public maxRecipientsPerReward;
    mapping(bytes32 => bool) public processedRewards;

    event RewardProcessed(
        bytes32 indexed rewardId,
        address[] recipients,
        uint256 amountPerRecipient,
        address indexed executor
    );

    constructor(
        address tokenAddress,
        address initialAdmin,
        uint256 _maxRecipientsPerReward
    ) {
        require(tokenAddress != address(0), "Invalid token address");
        require(initialAdmin != address(0), "Invalid admin address");
        require(_maxRecipientsPerReward >= 1, "Max must be >= 1");

        token = HiloToken(tokenAddress);
        maxRecipientsPerReward = _maxRecipientsPerReward;

        _grantRole(DEFAULT_ADMIN_ROLE, initialAdmin);
        _grantRole(ADMIN_ROLE, initialAdmin);
        _grantRole(PAUSER_ROLE, initialAdmin);
    }

    function processReward(
        bytes32 rewardId,
        address[] calldata recipients,
        uint256 amountPerRecipient
    )
        external
        onlyRole(EXECUTOR_ROLE)
        whenNotPaused
    {
        require(recipients.length >= 1, "Empty recipients");
        require(
            recipients.length <= maxRecipientsPerReward,
            "Too many recipients"
        );
        require(
            !processedRewards[rewardId],
            "Reward already processed"
        );

        for (uint256 i = 0; i < recipients.length; i++) {
            require(
                recipients[i] != address(0),
                "Invalid recipient"
            );
            for (uint256 j = i + 1; j < recipients.length; j++) {
                require(
                    recipients[i] != recipients[j],
                    "Duplicate recipients"
                );
            }
        }

        processedRewards[rewardId] = true;

        for (uint256 i = 0; i < recipients.length; i++) {
            token.mint(recipients[i], amountPerRecipient);
        }

        emit RewardProcessed(
            rewardId,
            recipients,
            amountPerRecipient,
            msg.sender
        );
    }

    function setMaxRecipientsPerReward(uint256 _max)
        external
        onlyRole(ADMIN_ROLE)
    {
        require(_max >= 1, "Max must be >= 1");
        maxRecipientsPerReward = _max;
    }

    function grantExecutor(address account)
        external
        onlyRole(ADMIN_ROLE)
    {
        grantRole(EXECUTOR_ROLE, account);
    }

    function revokeExecutor(address account)
        external
        onlyRole(ADMIN_ROLE)
    {
        revokeRole(EXECUTOR_ROLE, account);
    }

    function grantPauser(address account)
        external
        onlyRole(ADMIN_ROLE)
    {
        grantRole(PAUSER_ROLE, account);
    }

    function revokePauser(address account)
        external
        onlyRole(ADMIN_ROLE)
    {
        revokeRole(PAUSER_ROLE, account);
    }

    function pause() external onlyRole(PAUSER_ROLE) {
        _pause();
    }

    function unpause() external onlyRole(PAUSER_ROLE) {
        _unpause();
    }

    function hasRewardBeenProcessed(bytes32 rewardId)
        external
        view
        returns (bool)
    {
        return processedRewards[rewardId];
    }

    function getTokenAddress() external view returns (address) {
        return address(token);
    }

    function supportsInterface(bytes4 interfaceId)
        public
        view
        override
        returns (bool)
    {
        return super.supportsInterface(interfaceId);
    }
}
