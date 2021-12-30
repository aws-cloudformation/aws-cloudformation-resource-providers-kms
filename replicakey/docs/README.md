# AWS::KMS::ReplicaKey

The AWS::KMS::ReplicaKey resource specifies a multi-region replica AWS KMS Key in AWS Key Management Service (AWS KMS).

## Syntax

To declare this entity in your AWS CloudFormation template, use the following syntax:

### JSON

<pre>
{
    "Type" : "AWS::KMS::ReplicaKey",
    "Properties" : {
        "<a href="#primarykeyarn" title="PrimaryKeyArn">PrimaryKeyArn</a>" : <i>String</i>,
        "<a href="#description" title="Description">Description</a>" : <i>String</i>,
        "<a href="#enabled" title="Enabled">Enabled</a>" : <i>Boolean</i>,
        "<a href="#keypolicy" title="KeyPolicy">KeyPolicy</a>" : <i>Map, String</i>,
        "<a href="#pendingwindowindays" title="PendingWindowInDays">PendingWindowInDays</a>" : <i>Integer</i>,
        "<a href="#tags" title="Tags">Tags</a>" : <i>[ <a href="tag.md">Tag</a>, ... ]</i>,
    }
}
</pre>

### YAML

<pre>
Type: AWS::KMS::ReplicaKey
Properties:
    <a href="#primarykeyarn" title="PrimaryKeyArn">PrimaryKeyArn</a>: <i>String</i>
    <a href="#description" title="Description">Description</a>: <i>String</i>
    <a href="#enabled" title="Enabled">Enabled</a>: <i>Boolean</i>
    <a href="#keypolicy" title="KeyPolicy">KeyPolicy</a>: <i>Map, String</i>
    <a href="#pendingwindowindays" title="PendingWindowInDays">PendingWindowInDays</a>: <i>Integer</i>
    <a href="#tags" title="Tags">Tags</a>: <i>
      - <a href="tag.md">Tag</a></i>
</pre>

## Properties

#### PrimaryKeyArn

Identifies the primary AWS KMS Key to create a replica of. Specify the Amazon Resource Name (ARN) of the AWS KMS Key. You cannot specify an alias or key ID. For help finding the ARN, see Finding the Key ID and ARN in the AWS Key Management Service Developer Guide.

_Required_: Yes

_Type_: String

_Minimum_: <code>1</code>

_Maximum_: <code>256</code>

_Update requires_: [Replacement](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-replacement)

#### Description

A description of the AWS KMS Key. Use a description that helps you to distinguish this AWS KMS Key from others in the account, such as its intended use.

_Required_: No

_Type_: String

_Maximum_: <code>8192</code>

_Update requires_: [Replacement](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-no-interrupt)

#### Enabled

Specifies whether the AWS KMS Key is enabled. Disabled AWS KMS Keys cannot be used in cryptographic operations.

_Required_: No

_Type_: Boolean

_Update requires_: [Replacement](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-no-interrupt)

#### KeyPolicy

The key policy that authorizes use of the AWS KMS Key. The key policy must observe the following rules.

_Required_: Yes

_Type_: Map, String

_Update requires_: [Replacement](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-no-interrupt)

#### PendingWindowInDays

Specifies the number of days in the waiting period before AWS KMS deletes an AWS KMS Key that has been removed from a CloudFormation stack. Enter a value between 7 and 30 days. The default value is 30 days.

_Required_: No

_Type_: Integer

_Update requires_: [Replacement](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-no-interrupt)

#### Tags

An array of key-value pairs to apply to this resource.

_Required_: No

_Type_: List of <a href="tag.md">Tag</a>

_Update requires_: [Replacement](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-no-interrupt)

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
