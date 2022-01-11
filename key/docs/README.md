# AWS::KMS::Key

The AWS::KMS::Key resource specifies an AWS KMS key in AWS Key Management Service (AWS KMS). Authorized users can use the AWS KMS key to encrypt and decrypt small amounts of data (up to 4096 bytes), but they are more commonly used to generate data keys. You can also use AWS KMS keys to encrypt data stored in AWS services that are integrated with AWS KMS or within their applications.

## Syntax

To declare this entity in your AWS CloudFormation template, use the following syntax:

### JSON

<pre>
{
    "Type" : "AWS::KMS::Key",
    "Properties" : {
        "<a href="#description" title="Description">Description</a>" : <i>String</i>,
        "<a href="#enabled" title="Enabled">Enabled</a>" : <i>Boolean</i>,
        "<a href="#enablekeyrotation" title="EnableKeyRotation">EnableKeyRotation</a>" : <i>Boolean</i>,
        "<a href="#keypolicy" title="KeyPolicy">KeyPolicy</a>" : <i>Map, String</i>,
        "<a href="#keyusage" title="KeyUsage">KeyUsage</a>" : <i>String</i>,
        "<a href="#keyspec" title="KeySpec">KeySpec</a>" : <i>String</i>,
        "<a href="#multiregion" title="MultiRegion">MultiRegion</a>" : <i>Boolean</i>,
        "<a href="#pendingwindowindays" title="PendingWindowInDays">PendingWindowInDays</a>" : <i>Integer</i>,
        "<a href="#tags" title="Tags">Tags</a>" : <i>[ <a href="tag.md">Tag</a>, ... ]</i>,
    }
}
</pre>

### YAML

<pre>
Type: AWS::KMS::Key
Properties:
    <a href="#description" title="Description">Description</a>: <i>String</i>
    <a href="#enabled" title="Enabled">Enabled</a>: <i>Boolean</i>
    <a href="#enablekeyrotation" title="EnableKeyRotation">EnableKeyRotation</a>: <i>Boolean</i>
    <a href="#keypolicy" title="KeyPolicy">KeyPolicy</a>: <i>Map, String</i>
    <a href="#keyusage" title="KeyUsage">KeyUsage</a>: <i>String</i>
    <a href="#keyspec" title="KeySpec">KeySpec</a>: <i>String</i>
    <a href="#multiregion" title="MultiRegion">MultiRegion</a>: <i>Boolean</i>
    <a href="#pendingwindowindays" title="PendingWindowInDays">PendingWindowInDays</a>: <i>Integer</i>
    <a href="#tags" title="Tags">Tags</a>: <i>
      - <a href="tag.md">Tag</a></i>
</pre>

## Properties

#### Description

A description of the AWS KMS key. Use a description that helps you to distinguish this AWS KMS key from others in the account, such as its intended use.

_Required_: No

_Type_: String

_Maximum_: <code>8192</code>

_Update requires_: [No interruption](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-no-interrupt)

#### Enabled

Specifies whether the AWS KMS key is enabled. Disabled AWS KMS keys cannot be used in cryptographic operations.

_Required_: No

_Type_: Boolean

_Update requires_: [No interruption](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-no-interrupt)

#### EnableKeyRotation

Enables automatic rotation of the key material for the specified AWS KMS key. By default, automation key rotation is not enabled.

_Required_: No

_Type_: Boolean

_Update requires_: [No interruption](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-no-interrupt)

#### KeyPolicy

The key policy that authorizes use of the AWS KMS key. The key policy must observe the following rules.

_Required_: Yes

_Type_: Map, String

_Update requires_: [No interruption](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-no-interrupt)

#### KeyUsage

Determines the cryptographic operations for which you can use the AWS KMS key. The default value is ENCRYPT_DECRYPT. This property is required only for asymmetric AWS KMS keys. You can't change the KeyUsage value after the AWS KMS key is created.

_Required_: No

_Type_: String

_Allowed Values_: <code>ENCRYPT_DECRYPT</code> | <code>SIGN_VERIFY</code>

_Update requires_: [Replacement](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-replacement)

#### KeySpec

Specifies the type of AWS KMS key to create. The default value is SYMMETRIC_DEFAULT. This property is required only for asymmetric AWS KMS keys. You can't change the KeySpec value after the AWS KMS key is created.

_Required_: No

_Type_: String

_Allowed Values_: <code>SYMMETRIC_DEFAULT</code> | <code>RSA_2048</code> | <code>RSA_3072</code> | <code>RSA_4096</code> | <code>ECC_NIST_P256</code> | <code>ECC_NIST_P384</code> | <code>ECC_NIST_P521</code> | <code>ECC_SECG_P256K1</code>

_Update requires_: [Replacement](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-replacement)

#### MultiRegion

Specifies whether the AWS KMS key should be Multi-Region. You can't change the MultiRegion value after the AWS KMS key is created.

_Required_: No

_Type_: Boolean

_Update requires_: [Replacement](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-replacement)

#### PendingWindowInDays

Specifies the number of days in the waiting period before AWS KMS deletes an AWS KMS key that has been removed from a CloudFormation stack. Enter a value between 7 and 30 days. The default value is 30 days.

_Required_: No

_Type_: Integer

_Update requires_: [No interruption](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-no-interrupt)

#### Tags

An array of key-value pairs to apply to this resource.

_Required_: No

_Type_: List of <a href="tag.md">Tag</a>

_Update requires_: [No interruption](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-no-interrupt)

## Return Values

### Ref

When you pass the logical ID of this resource to the intrinsic `Ref` function, Ref returns the KeyId.

### Fn::GetAtt

The `Fn::GetAtt` intrinsic function returns a value for a specified attribute of this type. The following are the available attributes and sample return values.

For more information about using the `Fn::GetAtt` intrinsic function, see [Fn::GetAtt](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/intrinsic-function-reference-getatt.html).

#### Arn

Returns the <code>Arn</code> value.

#### KeyId

Returns the <code>KeyId</code> value.
