package com.metadium.did;

import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.text.ParseException;
import java.util.List;

import org.web3j.crypto.ECKeyPair;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.utils.Numeric;

import com.metadium.did.contract.IdentityRegistry;
import com.metadium.did.crypto.MetadiumKey;
import com.metadium.did.exception.DidException;
import com.metadium.did.protocol.JSONRPCException;
import com.metadium.did.protocol.MetaDelegator;
import com.metadium.did.util.Web3jUtils;
import com.metadium.did.wapper.NotSignTransactionManager;
import com.metadium.did.wapper.ZeroContractGasProvider;
import com.nimbusds.jose.util.JSONObjectUtils;

import net.minidev.json.JSONObject;

/**
 * Metadium DID.
 * 
 * Has key, did
 * 
 * @author ybjeon
 *
 */
public class MetadiumWallet {
	private MetadiumKey key;
	private String did;

	private MetadiumWallet(MetadiumKey key) {
		this.key = key;
	}
	
	private MetadiumWallet(String did, MetadiumKey key) {
		this.did = did;
		this.key = key;
	}
	
	/**
	 * Get wallet
	 * @return
	 */
	public MetadiumKey getKey() {
		return key;
	}
	
	/**
	 * create DID
	 * @param metaDelegator
	 * @return
	 * @throws DidException
	 */
	public static MetadiumWallet createDid(MetaDelegator metaDelegator) throws DidException {
		try {
			MetadiumWallet metadiumDid = new MetadiumWallet(new MetadiumKey());
			
			String txHash = metaDelegator.createIdentityDelegated(metadiumDid.key);
			TransactionReceipt transactionReceipt = Web3jUtils.ethGetTransactionReceipt(metaDelegator.getWeb3j(), txHash);
			
			if (transactionReceipt.getStatus().equals("0x1")) {
		        IdentityRegistry identityRegistry = IdentityRegistry.load(
		                metaDelegator.getAllServiceAddress().identityRegistry,
		                metaDelegator.getWeb3j(),
		                new NotSignTransactionManager(metaDelegator.getWeb3j()),
		                new ZeroContractGasProvider()
		        );
				
		        List<IdentityRegistry.IdentityCreatedEventResponse> responses = identityRegistry.getIdentityCreatedEvents(transactionReceipt);
	            if(responses.size() > 0){
	            	metadiumDid.did = metaDelegator.einToDid(responses.get(0).ein);
	                
	                String result = metaDelegator.addPublicKeyDelegated(metadiumDid.key, metadiumDid.key.getPublicKey());
	                TransactionReceipt addPublicReceipt = Web3jUtils.ethGetTransactionReceipt(metaDelegator.getWeb3j(), result);
	                if(addPublicReceipt.getStatus().equals("0x1")){
	                    return metadiumDid;
	                }
	                else {
	                	throw new DidException("Failed to add public_key. tx is "+addPublicReceipt.getTransactionHash());
	                }
	            }
	            else {
	            	throw new DidException("Failed to create DID. bad event");
	            }
			}
			else {
				throw new DidException("Failed to create DID. tx is "+transactionReceipt.getTransactionHash());
			}
		}
		catch (IOException | JSONRPCException | InvalidAlgorithmParameterException e) {
			throw new DidException(e);
		}
	}
	
	/**
	 * update key
	 * 
	 * @param metaDelegator
	 * @param newKey
	 * @throws DidException
	 */
	public void updateKeyOfDid(MetaDelegator metaDelegator, MetadiumKey newKey) throws DidException {
		try {
			// add associated address
			String txHash = metaDelegator.addAssociatedAddressDelegated(key, newKey);
			TransactionReceipt transactionReceipt = Web3jUtils.ethGetTransactionReceipt(metaDelegator.getWeb3j(), txHash);
			if (transactionReceipt.getStatus().equals("0x1")) {
				// add public key
				txHash = metaDelegator.addPublicKeyDelegated(newKey, newKey.getPublicKey());
				transactionReceipt = Web3jUtils.ethGetTransactionReceipt(metaDelegator.getWeb3j(), txHash);
				if (transactionReceipt.getStatus().equals("0x1")) {
					// remove old public key
					txHash = metaDelegator.removePublicKeyDelegated(key);
					transactionReceipt = Web3jUtils.ethGetTransactionReceipt(metaDelegator.getWeb3j(), txHash);
					if (transactionReceipt.getStatus().equals("0x1")) {
						// remove old associated address
						txHash = metaDelegator.removeAssociatedAddressDelegated(key);
						transactionReceipt = Web3jUtils.ethGetTransactionReceipt(metaDelegator.getWeb3j(), txHash);
						if (transactionReceipt.getStatus().equals("0x1")) {
							key = newKey;
						}
						else {
							metaDelegator.addPublicKeyDelegated(key, key.getPublicKey());
							metaDelegator.removePublicKeyDelegated(newKey);
							metaDelegator.removeAssociatedAddressDelegated(newKey);
							
							throw new DidException("Failed to remove old associated_key. tx is "+transactionReceipt.getTransactionHash());
						}
					}
					else {
						metaDelegator.removePublicKeyDelegated(newKey);
						metaDelegator.removeAssociatedAddressDelegated(newKey);
						
						throw new DidException("Failed to remove old public_key. tx is "+transactionReceipt.getTransactionHash());
					}
				}
				else {
					txHash = metaDelegator.removeAssociatedAddressDelegated(newKey);
					throw new DidException("Failed to add public_key. tx is "+transactionReceipt.getTransactionHash());
				}
			}
			else {
				throw new DidException("Failed to add associated_key. tx is "+transactionReceipt.getTransactionHash());
			}
		} catch (Exception e) {
			throw new DidException(e);
		}
	}
	
	/**
	 * did 삭제
	 * 
	 * @param metaDelegator
	 * @throws DidException
	 */
	public void deleteDid(MetaDelegator metaDelegator) throws DidException {
		try {
			metaDelegator.removePublicKeyDelegated(key);
			metaDelegator.removeAssociatedAddressDelegated(key);
		} catch (Exception e) {
			throw new DidException("Failed to delete.");
		}
	}
	
	/**
	 * Get did
	 * @return
	 */
	public String getDid() {
		return did;
	}
	
	/**
	 * Get key id
	 * @return
	 */
	public String getKid() {
		return did+"#MetaManagementKey#"+Numeric.cleanHexPrefix(key.getAddress());
	}
	
	/**
	 * to json string. {"did":"meta:did:0000...er43", "private_key":"3d3ac083d.."}
	 * @return
	 */
	public String toJson() {
		JSONObject object = new JSONObject();
		object.put("did", did);
		object.put("private_key", Numeric.toHexStringNoPrefixZeroPadded(key.getPrivateKey(), 64));
		return object.toJSONString();
	}
	
	/**
	 * from json string to object.
	 * 
	 * @param json
	 * @return
	 * @throws ParseException
	 */
	public static MetadiumWallet fromJson(String json) throws ParseException {
		JSONObject object = JSONObjectUtils.parse(json);
		String did = object.getAsString("did");
		BigInteger privateKey = Numeric.toBigInt(object.getAsString("private_key"));
		
		return new MetadiumWallet(did, new MetadiumKey(ECKeyPair.create(privateKey)));
	}
}
