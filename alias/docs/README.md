# AWS::KMS::Alias

The AWS::KMS::Alias resource specifies a display name for a customer master key (CMK) in AWS Key Management Service (AWS KMS). You can use an alias to identify a CMK in cryptographic operations.

## Syntax

To declare this entity in your AWS CloudFormation template, use the following syntax:

### JSON

<pre>
{
    "Type" : "AWS::KMS::Alias",
    "Properties" : {
        "<a href="#aliasname" title="AliasName">AliasName</a>" : <i>String</i>,
        "<a href="#targetkeyid" title="TargetKeyId">TargetKeyId</a>" : <i>String</i>
    }
}
</pre>

### YAML

<pre>
Type: AWS::KMS::Alias
Properties:
    <a href="#aliasname" title="AliasName">AliasName</a>: <i>String</i>
    <a href="#targetkeyid" title="TargetKeyId">TargetKeyId</a>: <i>String</i>
</pre>

## Properties

#### AliasName

Specifies the alias name. This value must begin with alias/ followed by a name, such as alias/ExampleAlias. The alias name cannot begin with alias/aws/. The alias/aws/ prefix is reserved for AWS managed CMKs.

_Required_: Yes

_Type_: String

_Minimum_: <code>1</code>

_Maximum_: <code>256</code>

_Pattern_: <code>^(alias/)[a-zA-Z0-9:/_-]+$</code>

_Update requires_: [Replacement](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-replacement)

#### TargetKeyId

Identifies the CMK to which the alias refers. Specify the key ID or the Amazon Resource Name (ARN) of the CMK. You cannot specify another alias. For help finding the key ID and ARN, see Finding the Key ID and ARN in the AWS Key Management Service Developer Guide.

_Required_: Yes

_Type_: String

_Minimum_: <code>1</code>

_Maximum_: <code>256</code>

_Update requires_: [No interruption](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-no-interrupt)

## Return Values

### Ref

When you pass the logical ID of this resource to the intrinsic `Ref` function, Ref returns the AliasName.
